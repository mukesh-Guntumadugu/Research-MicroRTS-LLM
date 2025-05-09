#!/bin/sh
#SBATCH --gpus-per-node=1
#SBATCH -e output.err
#SBATCH -o output.out
#SBATCH --nodelist=node008

# Returns response as structured json (no chat history)
MODEL=$1
PROMPT=$(cat $2)
FORMAT=$(cat $3)

curl -X POST http://localhost:11434/api/generate -H "Content-Type: application/json" -d "{
  \"model\": \"$MODEL\",
  \"prompt\": \"$PROMPT\",
  \"stream\": false,
  \"format\": $FORMAT
}"