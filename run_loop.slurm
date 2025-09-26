#!/bin/bash
#SBATCH --job-name=make_run_loop
#SBATCH --output=slurm_%j.out          # avoid 'logs/' here; dir may not exist yet
#SBATCH --partition=defq
#SBATCH --time=06:00:00
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=12              # change to 8/16 if needed
# DO NOT use: #SBATCH --cpu-bind=cores  (this is what caused the error)
# NOTE: DO NOT add --mem on this cluster

set -euo pipefail
set -x

# --- Setup ---
cd "$SLURM_SUBMIT_DIR"
mkdir -p logs results tmp
export JOBROOT="$SLURM_SUBMIT_DIR"
export LOG_DIR="$JOBROOT/logs"
export RESULTS_ROOT="$JOBROOT/results/$SLURM_JOB_ID"
export TMPDIR="$JOBROOT/tmp/$SLURM_JOB_ID"
mkdir -p "$RESULTS_ROOT" "$TMPDIR"

export PYTHONUNBUFFERED=1
export JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -XX:ActiveProcessorCount=${SLURM_CPUS_PER_TASK:-4} -XX:ParallelGCThreads=${SLURM_CPUS_PER_TASK:-4} -XX:ConcGCThreads=${SLURM_CPUS_PER_TASK:-4}"
# export OMP_NUM_THREADS=${SLURM_CPUS_PER_TASK:-4} MKL_NUM_THREADS=${SLURM_CPUS_PER_TASK:-4} OPENBLAS_NUM_THREADS=${SLURM_CPUS_PER_TASK:-4}

trap 'echo "[$(date)] Caught SIGTERM, exiting cleanly"; exit 0' SIGTERM

STDBUF=""
if command -v stdbuf >/dev/null 2>&1; then
  STDBUF="stdbuf -oL -eL"
fi

# Use srun for proper resource binding
SRUN="srun --ntasks=1 --cpus-per-task=${SLURM_CPUS_PER_TASK:-4} --cpu-bind=cores"

while true; do
  ts="$(date +'%Y-%m-%d_%H-%M-%S')"
  RUN_DIR="$RESULTS_ROOT/$ts"
  RUN_LOG="$LOG_DIR/run_${ts}.log"
  mkdir -p "$RUN_DIR"

  echo "=== Start run at $ts on $(hostname) CWD=$(pwd) ==="
  echo "RESULTS -> $RUN_DIR"
  echo "TMPDIR  -> $TMPDIR"

  ${SRUN} ${STDBUF} make run ARGS="--no-gui" 2>&1 \
    | awk '{ print strftime("[%F %T]"), $0; fflush(); }' \
    | tee -a "$RUN_LOG"

  echo "=== End run at $(date +'%Y-%m-%d_%H-%M-%S') ==="
  sleep 300   # 5 minutes between runs
done
