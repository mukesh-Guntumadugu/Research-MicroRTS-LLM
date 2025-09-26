#!/bin/bash
#SBATCH --job-name=make_run_loop
#SBATCH --output=slurm_%j.out            # keep simple; logs/ may not exist yet
#SBATCH --partition=defq
#SBATCH --time=06:00:00
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=12               # adjust to 8/16 if needed
# NOTE: do NOT add --mem on this cluster

# ---- Safety / debug ---------------------------------------------------------
set -euo pipefail
set -x

# ---- Init paths -------------------------------------------------------------
cd "$SLURM_SUBMIT_DIR"
mkdir -p logs results tmp

export JOBROOT="$SLURM_SUBMIT_DIR"
export LOG_DIR="$JOBROOT/logs"
export RESULTS_ROOT="$JOBROOT/results/$SLURM_JOB_ID"
export TMPDIR="$JOBROOT/tmp/$SLURM_JOB_ID"
mkdir -p "$RESULTS_ROOT" "$TMPDIR"

# ---- Runtime env ------------------------------------------------------------
export PYTHONUNBUFFERED=1
export JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -XX:ActiveProcessorCount=${SLURM_CPUS_PER_TASK:-4} -XX:ParallelGCThreads=${SLURM_CPUS_PER_TASK:-4} -XX:ConcGCThreads=${SLURM_CPUS_PER_TASK:-4}"
# If you need BLAS/OpenMP caps, uncomment:
# export OMP_NUM_THREADS=${SLURM_CPUS_PER_TASK:-4}
# export MKL_NUM_THREADS=${SLURM_CPUS_PER_TASK:-4}
# export OPENBLAS_NUM_THREADS=${SLURM_CPUS_PER_TASK:-4}

# stdbuf (optional, if available) to line-buffer stdout/err
STDBUF=""
if command -v stdbuf >/dev/null 2>&1; then
  STDBUF="stdbuf -oL -eL"
fi

# Use srun for proper resource allocation; avoid cpu-bind=cores (caused errors)
SRUN="srun --ntasks=1 --cpus-per-task=${SLURM_CPUS_PER_TASK:-4} --cpu-bind=none"

# Loop sleep (seconds) can be overridden: SLEEP_SECONDS=60 sbatch run_loop.slurm
SLEEP_SECONDS="${SLEEP_SECONDS:-300}"

# Graceful shutdown: finish current iteration, then exit
STOP=0
trap 'echo "[$(date)] SIGTERM caught — will stop after this iteration."; STOP=1' SIGTERM

# Optional: verify we can run "make run"
if ! make -n run >/dev/null 2>&1; then
  echo "ERROR: 'make run' target not found in $(pwd). Exiting."
  exit 2
fi

echo "===== Job start: $(date) on $(hostname) CWD=$(pwd) ====="
echo "RESULTS_ROOT=$RESULTS_ROOT"
echo "TMPDIR=$TMPDIR"
echo "LOG_DIR=$LOG_DIR"
echo "CPUS_PER_TASK=${SLURM_CPUS_PER_TASK:-unset}"

while true; do
  ts="$(date +'%Y-%m-%d_%H-%M-%S')"
  RUN_DIR="$RESULTS_ROOT/$ts"
  RUN_LOG="$LOG_DIR/run_${ts}.log"
  mkdir -p "$RUN_DIR"

  echo "=== Start run at $ts ===" | tee -a "$RUN_LOG"
  echo "Run dir: $RUN_DIR" | tee -a "$RUN_LOG"

  # Allow failures in the pipeline without killing the loop
  set +e
  ${SRUN} ${STDBUF} make run ARGS="--no-gui" 2>&1 \
    | awk '{ print strftime("[%F %T]"), $0; fflush(); }' \
    | tee -a "$RUN_LOG"
  rc=${PIPESTATUS[0]}   # exit code of srun/make
  set -e

  echo "=== End run at $(date +'%F_%T'); exit code: ${rc} ===" | tee -a "$RUN_LOG"

  # (Optional) If you want to copy artifacts from your app into RUN_DIR, do it here.
  # Example:
  # [ -d outputs ] && cp -r outputs "$RUN_DIR/" || true

  # Break cleanly if we were asked to stop
  if [ "$STOP" -eq 1 ]; then
    echo "STOP requested; exiting loop."
    break
  fi

  # Sleep between runs
  echo "Sleeping ${SLEEP_SECONDS}s before next run..." | tee -a "$RUN_LOG"
  sleep "$SLEEP_SECONDS"
done

echo "===== Job end  -> :    $(date) ====="
