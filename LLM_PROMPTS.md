## Static Prompt
The static prompt is unchanging and is included in every prompt to the LLM agent.
```md
Game Rules:
    Two players, Player 1 (Ally) and Player 2 (Enemy) are competing to eliminate all opposing enemy units in a Real Time Strategy (RTS) game.
    Each step, each player can assign actions to their units if they are not already doing an action. Each unit can only be assigned ONE action.
    Players can only assign actions to their ally units.
    There are 6 available actions:
    - `move` - Unit will move to target location.
        - Arguments: ((Target_x, Target_y))
    - `train` - Unit will train the provided unit type (only bases and barracks can use this action).
        - Arguments: (Unit_Type)
    - `build` - Unit will build the provided building type at the target location, consuming the resource cost from the ally base (only workers can use this action).
        - Arguments: ((Target_x, Target_y), Building_Type)
    - `harvest` - Unit will navigate to the target resource, collect a resource and bring it back to the target ally base.
        - Arguments: ((Resource_x, Resource_y), (Ally_Base_x, Ally_Base_y))
    - `attack` - Unit will navigate to, and attack the target enemy.
        - Arguments: ((Enemy_x, Enemy_y))
    - `idle` - The target unit will do nothing for a round. This is the default for all available units that are not assigned an action.
        - Arguments: ()
    The game is over once all units and buildings from either team are killed or destroyed, the remaining team is the winner.

    Unit types:
    | Unit Type | HP | Cost | Attack Damage | Attack Range | Speed | Abilities                                                       |
    |-----------|----|------|---------------|--------------|-------|-----------------------------------------------------------------|
    | worker    | 1  | 1    | 1             | 1            | 1     | Trained from base, Gathers resources, builds base and barracks  |
    | light     | 4  | 2    | 2             | 1            | 2     | Trained from barracks, High Speed                               |
    | heavy     | 8  | 3    | 4             | 1            | 1     | Trained from barracks, High HP, High Damage                     |
    | ranged    | 3  | 2    | 1             | 3            | 1     | Trained from barracks, High Range                               |

    Building types:
    | Building Type | HP  | Cost | Abilities                               |
    |---------------|-----|------|-----------------------------------------|
    | base          | 10  | 10   | Produces workers, Stores resources      |
    | barracks      | 5   | 5    | Produces Light, Heavy, and Ranged units |

    Suggested strategy:
    1. Early Game - Economy Focus
        - Harvest nonstop with workers.
        - Build barracks once you have 5 resources.
    2. Mid Game - Army Development
        - Train heavies, ranged, and lights using the barracks.
        - Hunt enemy workers to slow their economy.
        - Keep barracks safe at all costs.
    3. Late Game - Closing Out
        - Group units and attack key targets together.
        - Destroy enemy production buildings first.
        - Maintain resource control to prevent comebacks.

    Game state format:
    The game state consists the map size and a list of feature locations (zero-indexed) within the the map bounds. Units and buildings have different properties associated with their current state. All units and buildings (except resources) have an 'available' property. If a unit or building is available an action issued to it will be accepted.

    Raw Move format: `(<X>, <Y>): <Unit_Type> <Action>(<Action_Arguments>)`
    - X: The X position of the unit to perform the action
    - Y: The Y position of the unit to perform the action
    - Unit_Type: The unit type of the unit performing the action
    - Action: The action for the unit to perform
    - Action_Arguments: The arguments of the action being performed
```

## Dynamic Prompt
The dynamic prompt changes based on the current game state, providing up to date information to the LLM to act upon. In action the dynamic prompt is appended to the static portion before being sent to the LLM. An example is shown below:
```json
Map size: 8x8

Turn: 0/5000

Max Actions: 2

Feature locations:
(0,0) Neutral Resource Node {resources=20}
(7,7) Neutral Resource Node {resources=20}
(1,1) Ally Worker Unit {current_action=idle, HP=1}
(2,1) Ally Base Unit {resources=5, current_action=idle, HP=1}
(6,6) Enemy Worker Unit {current_action=idle, HP=1}
(5,6) Enemy Base Unit {resources=5, current_action=idle, HP=1}
```

## Response Schema
The JSON response structure the LLM fills out.
```json
{
    "type": "object",
    "properties": {
        "thinking": {
            "type": "string",
            "description": "Plan out what moves you should take"
        },
        "moves": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "raw_move": { "type": "string" },
                    "unit_position": {
                        "type": "array",
                        "items": { "type": "integer" },
                        "minItems": 2,
                        "maxItems": 2
                    },
                    "unit_type": {
                        "type": "string",
                        "enum": ["worker", "light", "heavy", "ranged", "base", "barracks"]
                    },
                    "action_type": {
                        "type": "string",
                        "enum": ["move", "train", "build", "harvest", "attack"]
                    }
                },
                "required": ["raw_move", "unit_position", "unit_type", "action_type"],
                "propertyOrdering": ["raw_move", "unit_position", "unit_type", "action_type"]
            }
        }
    },
    "required": ["thinking", "moves"],
    "propertyOrdering": ["thinking", "moves"]
}
```
