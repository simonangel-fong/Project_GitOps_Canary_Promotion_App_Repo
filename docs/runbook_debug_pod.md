# Runbook - Debugging a Failing Pod

[Back](../README.md)

- [Runbook - Debugging a Failing Pod](#runbook---debugging-a-failing-pod)
  - [When to Use This Runbook](#when-to-use-this-runbook)
  - [Common Pod Failure Modes](#common-pod-failure-modes)
  - [Triage Flow](#triage-flow)
  - [Step 1 — Check Pod Phase and Status](#step-1--check-pod-phase-and-status)
  - [Step 2 — Read the Events](#step-2--read-the-events)
  - [Step 3 — Decode the Container State](#step-3--decode-the-container-state)
  - [Step 4 — Read the Logs (Current and Previous)](#step-4--read-the-logs-current-and-previous)
  - [Step 5 — Inspect Resources and Limits](#step-5--inspect-resources-and-limits)
  - [Step 6 — Exec Into the Pod (if it's running)](#step-6--exec-into-the-pod-if-its-running)
  - [Step 7 — Check the Node](#step-7--check-the-node)
  - [Failure Mode → Fix Cheatsheet](#failure-mode--fix-cheatsheet)
  - [Useful One-Liners](#useful-one-liners)

---

## When to Use This Runbook

Use this runbook whenever a pod is **not Running**, **not Ready**, or **restarting repeatedly**. It covers the full triage path — from `kubectl get pods` to root cause — so you do not jump straight to `kubectl logs` and miss the real signal (as happened with the OOMKilled backend, where the JVM log was empty but `kubectl describe` made the cause obvious).

Applies to any pod in this project (backend, frontend, future services).

---

## Common Pod Failure Modes

Group failures by **where in the lifecycle they happen**. This is the mental model for the triage flow below.

| Lifecycle phase     | Symptom (`STATUS` column)          | Common causes                                                                                                                                |
| ------------------- | ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Scheduling          | `Pending`                          | Insufficient CPU/memory on any node, unsatisfiable node selector / affinity / taints, no matching PVC, ResourceQuota exceeded                |
| Image pull          | `ErrImagePull`, `ImagePullBackOff` | Wrong image name or tag, private registry without `imagePullSecrets`, registry rate-limited, network egress blocked                          |
| Container start     | `CreateContainerConfigError`       | Referenced `Secret` / `ConfigMap` missing or key not found, invalid env var reference, bad volume mount                                      |
| Container start     | `RunContainerError`                | Bad `command` / `args`, missing executable, permission denied (read-only FS, non-root user mismatch)                                         |
| Running but failing | `CrashLoopBackOff`                 | App throws on startup (config, missing env, DB unreachable), liveness probe failing, OOMKilled by cgroup (exit 137), app exits 0 immediately |
| Running but failing | `Running` + `0/1 READY`            | Readiness probe failing (app slow to start, wrong path / port, dependency down)                                                              |
| Running but failing | `OOMKilled` in `lastState`         | Memory limit too low for JVM non-heap usage, real memory leak, off-heap allocation                                                           |
| Termination         | `Terminating` (stuck)              | Finalizer not released, graceful shutdown longer than `terminationGracePeriodSeconds`, volume detach hang                                    |
| Eviction            | `Evicted`                          | Node under memory / disk pressure, pod exceeded ephemeral storage, node draining                                                             |
| Networking          | Running but unreachable            | Service selector mismatch, wrong `containerPort`, NetworkPolicy blocking, DNS failure inside the pod                                         |

---

## Triage Flow

Always go top-down. Each step rules out a layer.

```
1. Pod phase  -->  is it even scheduled and started?
2. Events     -->  what did the kubelet / scheduler complain about?
3. Container state --> why did the container stop or fail to start?
4. Logs (current AND previous) --> what did the app say before it died?
5. Resources / limits --> was it killed by the cgroup?
6. Exec in --> is the runtime config (env, files, DNS) what you expect?
7. Node --> is the underlying node healthy?
```

The mistake to avoid: jumping straight to step 4. If the container was `OOMKilled` by the kernel, the app log is truncated and useless — steps 2, 3, and 5 are where the answer lives.

---

## Step 1 — Check Pod Phase and Status

```sh
kubectl get pods -o wide
kubectl get pod <pod> -o wide
```

What to read:

- **`STATUS`** — `Pending`, `ContainerCreating`, `Running`, `CrashLoopBackOff`, `ImagePullBackOff`, `Error`, `OOMKilled`, `Completed`, `Evicted`, `Terminating`.
- **`READY`** — `0/1` while `Running` means the readiness probe is failing.
- **`RESTARTS`** — non-zero with a recent age means the container is crashing in a loop.
- **`NODE`** — useful for step 7.

Decision:

- `Pending` → jump to **Step 2** (events will say why).
- `ImagePullBackOff` / `ErrImagePull` → **Step 2**.
- `CrashLoopBackOff` → **Step 3** then **Step 4 with `--previous`**.
- `Running` but `0/1` → **Step 3** (look at probe failures in events) then **Step 4**.
- `Evicted` / `Terminating` stuck → **Step 7**.

---

## Step 2 — Read the Events

Events are the kubelet and scheduler narrating what they tried to do.

```sh
kubectl describe pod <pod>
# scroll to the Events: section at the bottom

# or just the events, sorted newest-last:
kubectl get events --sort-by=.lastTimestamp --field-selector involvedObject.name=<pod>
```

Look for, in order of how often they explain things:

- `FailedScheduling` — scheduler couldn't place the pod. The message names the constraint (`Insufficient memory`, `node(s) had untolerated taint`, `0/3 nodes are available`).
- `Failed` / `ErrImagePull` / `ImagePullBackOff` — kubelet couldn't pull the image. Check the image name, tag, and registry auth.
- `FailedMount` / `FailedAttachVolume` — volume / Secret / ConfigMap problem.
- `Unhealthy` — probe failed. The message says which probe (`Liveness probe failed: ...`, `Readiness probe failed: ...`) and the HTTP status or connection error.
- `BackOff` — kubelet is throttling restarts because the container keeps failing.
- `Killing` — kubelet decided to kill the container. Usually paired with a probe failure or a SIGTERM during rollout.

---

## Step 3 — Decode the Container State

`kubectl describe pod` also shows `State`, `Last State`, and `Reason` for each container. This is the single most useful field when the log is empty.

```sh
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].state}{"\n"}'
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].lastState}{"\n"}'
kubectl get pod <pod> -o jsonpath='{.status.containerStatuses[0].lastState.terminated.reason}{"\t"}{.status.containerStatuses[0].lastState.terminated.exitCode}{"\n"}'
```

How to read the `reason` / `exitCode` pair:

| `reason`                       | `exitCode` | Meaning                                                                                                |
| ------------------------------ | ---------- | ------------------------------------------------------------------------------------------------------ |
| `OOMKilled`                    | `137`      | Kernel SIGKILL because the container exceeded its memory limit. App log is usually truncated.          |
| `Error`                        | non-zero   | Process exited on its own with a non-zero code — read **Step 4** logs.                                 |
| `Completed`                    | `0`        | Process exited cleanly. For a long-running service this is still a bug (app shouldn't exit).           |
| `ContainerCannotRun`           | varies     | exec / command problem at startup.                                                                     |
| `CrashLoopBackOff`             | n/a        | Status, not a reason — drill into `lastState.terminated.reason` for the real cause.                    |
| `CreateContainerConfigError`   | n/a        | Missing Secret/ConfigMap reference. Events will name it.                                               |
| `DeadlineExceeded`             | varies     | activeDeadlineSeconds elapsed.                                                                         |
| Killed by signal (no `reason`) | `143`      | SIGTERM — usually a rollout / scale-down, not a bug.                                                   |

Exit code shortcut: **128 + signal**. So `137 = 128 + 9 = SIGKILL` (almost always OOMKilled), `143 = 128 + 15 = SIGTERM` (graceful shutdown).

---

## Step 4 — Read the Logs (Current and Previous)

Always read the **previous** container's log when debugging a restart — the current log starts after the restart and won't contain the crash.

```sh
kubectl logs <pod>                       # current container
kubectl logs <pod> --previous            # the one that just died
kubectl logs <pod> -c <container>        # multi-container pod
kubectl logs <pod> --since=10m           # narrow the window
kubectl logs <pod> -f                    # follow

# all containers, both current and previous:
kubectl logs <pod> --all-containers --previous
```

What to look for:

- Stack traces, `Caused by:` chains.
- The **last few lines before the gap** — for OOMKilled apps the log just stops mid-sentence.
- Probe failure messages on the app side (e.g. `/api/healthz` returning 503).
- Spring Boot startup errors (`APPLICATION FAILED TO START`, bean wiring failures, port already in use).

If the log is empty or cut off and the container `reason` is `OOMKilled` (Step 3), the JVM **never got to log the OOM** — see the cheatsheet for the fix.

---

## Step 5 — Inspect Resources and Limits

```sh
# what the pod was asked to run with:
kubectl get pod <pod> -o jsonpath='{.spec.containers[*].resources}{"\n"}'

# what it's actually using right now (needs metrics-server):
kubectl top pod <pod> --containers
kubectl top node
```

Decision points:

- Container `OOMKilled` and the limit is close to actual usage → raise `limits.memory`, or lower the JVM heap percentage so non-heap memory fits inside the limit. For Spring Boot under `-XX:MaxRAMPercentage=75`, a 256Mi limit leaves only ~60Mi for metaspace + threads + code cache, which is often too tight.
- Pod `Pending` with `Insufficient memory` event → either the request is too high for any node, or the cluster is full.
- CPU throttling (high `cpu` usage hitting the limit) won't kill the pod but will cause readiness probe timeouts that look like a crash.

---

## Step 6 — Exec Into the Pod (if it's running)

Only useful when the pod is `Running` but misbehaving (readiness failing, wrong config, networking broken).

```sh
kubectl exec -it <pod> -- sh
# or for distroless / minimal images, use an ephemeral debug container:
kubectl debug -it <pod> --image=busybox --target=<container>
```

Things to check from inside:

- `env` — are the env vars what you expected? (catches typos in ConfigMap keys)
- `cat /etc/resolv.conf` and `nslookup <service>` — DNS working?
- `wget -qO- http://localhost:8080/api/healthz` — does the app respond to its **own** probe URL on the **container** port?
- `ls /mnt/...` — is the mounted Secret / ConfigMap present and readable?

If `localhost:<containerPort>` works but the Service doesn't, the problem is the Service selector or `containerPort`, not the app.

---

## Step 7 — Check the Node

If multiple pods on the same node are unhealthy, suspect the node.

```sh
kubectl get nodes
kubectl describe node <node>     # check Conditions: MemoryPressure, DiskPressure, PIDPressure, Ready
kubectl get events --field-selector involvedObject.kind=Node
```

Look for `MemoryPressure=True` (causes evictions), `DiskPressure=True` (image GC, evictions), `Ready=False` (kubelet not reporting).

---

## Failure Mode → Fix Cheatsheet

| Symptom                                            | First check                                                  | Typical fix                                                                                                                            |
| -------------------------------------------------- | ------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| `Pending` — `Insufficient memory/cpu`              | `kubectl describe pod` events                                | Lower `requests`, scale the node group, or remove unschedulable workloads.                                                             |
| `ImagePullBackOff`                                 | Exact image string in events                                 | Fix tag, push the image, add `imagePullSecrets`, or check registry auth.                                                               |
| `CreateContainerConfigError`                       | Events name the missing Secret/ConfigMap                     | Create it or fix the `valueFrom` key.                                                                                                  |
| `CrashLoopBackOff` + log shows stack trace         | `kubectl logs --previous`                                    | Fix the app bug or the bad config.                                                                                                     |
| `CrashLoopBackOff` + `lastState.reason: OOMKilled` | Memory limit vs. JVM heap settings                           | Raise `limits.memory`, **or** lower `-XX:MaxRAMPercentage`, **and** add `-XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError`. |
| `Running` `0/1 READY`                              | Events show `Readiness probe failed`                         | Fix probe `path`/`port`, raise `initialDelaySeconds`, or fix the dependency the app waits on.                                          |
| Liveness restarts during long startup              | `initialDelaySeconds` vs. real startup time                  | Use a `startupProbe` so liveness only kicks in once the app is up.                                                                     |
| `Evicted`                                          | `kubectl describe node` conditions                           | Set `requests` properly so the pod is in a better QoS class; reduce node pressure.                                                     |
| `Terminating` stuck                                | `kubectl get pod <pod> -o yaml` → `metadata.finalizers`      | Remove the offending finalizer (carefully) or fix the controller that owns it.                                                         |
| Service reachable from inside pod but not outside  | `kubectl get endpoints <svc>`                                | Service selector doesn't match pod labels, or `targetPort` ≠ `containerPort`.                                                          |
| App log shows nothing before death                 | `kubectl describe pod` → `Last State`                        | If `OOMKilled`, the kernel killed the process before the JVM could flush — see the OOM row above.                                      |

---

## Useful One-Liners

```sh
# Everything wrong in the namespace, at a glance:
kubectl get pods --field-selector=status.phase!=Running

# Most recent events, newest last (the way humans read):
kubectl get events --sort-by=.lastTimestamp | tail -30

# Why did the previous container of every pod die?
kubectl get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.containerStatuses[0].lastState.terminated.reason}{"\t"}{.status.containerStatuses[0].lastState.terminated.exitCode}{"\n"}{end}'

# Tail the previous container of a crash-looping pod:
kubectl logs <pod> --previous --tail=200

# Watch a pod come up:
kubectl get pod <pod> -w

# Resource usage right now (needs metrics-server):
kubectl top pod -A --sort-by=memory | head
```
