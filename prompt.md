Game Rules:
Two players, Player 1 (Ally) and Player 2 (Enemy) are competing to eliminate all opposing enemy units in a Real Time Strategy (RTS) game.
Each step, each player can assign actions to their units if they are not already doing an action.
There are 6 available actions:
- move((Target_x, Target_y)): Target ally will move to the target location.
- train(Unit_Type): Target ally base will train the provided unit type, consuming 1 resource.
- build((Target_x, Target_y), Building_Type): Target ally worker will build the provided building type at the target location, consuming the resource cost from the ally base.
- harvest((Resource_x, Resource_y), (Ally_Base_x, Ally_Base_y)): Target ally will navigate to the target resource, collect a resource and bring it back to the target ally base.
- attack((Enemy_x, Enemy_y)): Target ally will navigate to, and attack the target enemy.
- idle(): The target unit will do nothing for a round. This is the default for all available units that are not assigned an action.
The game is over once all units and buildings from either team are killed or destroyed, the remaining team is the winner.

Unit types:
| Unit Type   | HP | Cost | Attack Damage | Attack Range | Speed | Abilities                             |
|-------------|----|------|---------------|--------------|-------|---------------------------------------|
| Worker      | 1  | 1    | 1             | 1            | 1     | Gathers resources, builds structures  |
| Light Unit  | 4  | 2    | 2             | 1            | 2     | High Speed                            |
| Heavy Unit  | 8  | 3    | 4             | 1            | 1     | High HP, High Damage                  |
| Ranged Unit | 3  | 2    | 1             | 3            | 1     | High Range                            |

Building types:
| Building Type | HP  | Cost | Abilities                               |
|---------------|-----|------|-----------------------------------------|
| Base          | 10  | 10   | Produces workers, Stores resources      |
| Barracks      | 5   | 5    | Produces Light, Heavy, and Ranged units |

Suggested strategy:
1. Early Game - Economy Focus
    - Harvest nonstop with workers.
    - Train 4-6 workers quickly.
    - Build barracks once you have 5 resources.
2. Mid Game - Army Development
    - Create balanced forces: heavies for defense, ranged for damage, lights for scouting.
    - Hunt enemy workers to slow their economy.
    - Keep barracks safe at all costs.
3. Late Game - Closing Out
    - Group units and attack key targets together.
    - Destroy enemy production buildings first.
    - Maintain resource control to prevent comebacks.
4. Golden Rule: Never stop producing units or workers. Every idle moment is wasted potential.

Game state format:
The game state consists the map size and a list of feature locations (zero-indexed) within the the map bounds. Units and buildings have different properties associated with their current state. All units and buildings (except resources) have an 'available' property. If a unit or building is available an action issued to it will be accepted.

Move format:
Return a list of actions to take for each available unit or building in the following format:
(<X>, <Y>): <Unit Type> <Action(any action arguments)>
(<X>, <Y>): <Unit Type> <Action(any action arguments)>
etc ... 

Map size: 8x8
Turn: 950/5000
Max actions: 2

Feature locations:
(0,0) Resource Node {resources=20}
(1,1) Ally Worker Unit {current_action="idling", holdingResource=false}
(2,1) Ally Base Unit {resources=5, current_action="idling"}
(5,6) Enemy Base Unit {resources=5, current_action="idling"}
(6,6) Enemy Worker Unit {current_action="idling", holdingResource=false}
(7,7) Resource Node {resources=20}