package ai.abstraction;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.ParameterSpecification;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import com.google.gson.*;
import rts.GameState;
import rts.PhysicalGameState;
import rts.UnitAction;
import rts.Player;
import rts.PlayerAction;
import rts.units.*;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 *
 * @author Brendan Smyers
 */
public class LLM_DeepseekR1 extends AbstractionLayerAI {
    static final String ENDPOINT_URL = "http://localhost:11434/api/generate";
    static final String MODEL = "deepseek-r1:70b";
    static final JsonObject MOVE_RESPONSE_SCHEMA;
    static final String PROJECT_PATH = "/home/bs602422/projects/MicroRTS-LLM";
    static final String MOVE_HISTORY_PATH = PROJECT_PATH + "/move_history.json";
    static String command = "sbatch --wait " + PROJECT_PATH + "/src/ai/abstraction/llm-json-completion.sh " + MODEL; // <prompt> <format> will be added later
    // How often the LLM should act on the game state
    // More frequent LLM intervention is not necessarily better
    // Low = more frequent, higher = less freqeunt
    static final Integer LLM_INTERVAL = 50;

    static {
        MOVE_RESPONSE_SCHEMA = new JsonObject();

        // Define coordinate schema
        JsonObject coord = new JsonObject();
        coord.addProperty("type", "array");
        JsonObject coordItems = new JsonObject();
        coordItems.addProperty("type", "integer");
        coord.add("items", coordItems);
        coord.addProperty("minItems", 2);
        coord.addProperty("maxItems", 2);

        // Define raw_move: string
        JsonObject rawMove = new JsonObject();
        rawMove.addProperty("type", "string");

        // Define unit_type and action_type
        JsonObject unitType = new JsonObject();
        unitType.addProperty("type", "string");
        JsonArray unitTypeEnum = new JsonArray();
        unitTypeEnum.add(new JsonPrimitive("worker"));
        unitTypeEnum.add(new JsonPrimitive("light"));
        unitTypeEnum.add(new JsonPrimitive("heavy"));
        unitTypeEnum.add(new JsonPrimitive("ranged"));
        unitTypeEnum.add(new JsonPrimitive("base"));
        unitTypeEnum.add(new JsonPrimitive("barracks"));
        unitType.add("enum", unitTypeEnum);

        JsonObject actionType = new JsonObject();
        actionType.addProperty("type", "string");
        JsonArray actionTypeEnum = new JsonArray();
        actionTypeEnum.add(new JsonPrimitive("move"));
        actionTypeEnum.add(new JsonPrimitive("train"));
        actionTypeEnum.add(new JsonPrimitive("build"));
        actionTypeEnum.add(new JsonPrimitive("harvest"));
        actionTypeEnum.add(new JsonPrimitive("attack"));
        // actionTypeEnum.add(new JsonPrimitive("idle")); // Disable idle for more ACTION!
        actionType.add("enum", actionTypeEnum);

        // Combine all properties into the object schema
        JsonObject itemSchema = new JsonObject();
        itemSchema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        properties.add("raw_move", rawMove);  // Add raw_move here
        properties.add("unit_position", coord);
        properties.add("unit_type", unitType);
        properties.add("action_type", actionType);

        itemSchema.add("properties", properties);

        JsonArray requiredFields = new JsonArray();
        requiredFields.add(new JsonPrimitive("raw_move"));
        requiredFields.add(new JsonPrimitive("unit_position"));
        requiredFields.add(new JsonPrimitive("unit_type"));
        requiredFields.add(new JsonPrimitive("action_type"));
        itemSchema.add("required", requiredFields);

        // Final response schema
        MOVE_RESPONSE_SCHEMA.addProperty("type", "array");
        MOVE_RESPONSE_SCHEMA.add("items", itemSchema);
    }


    String PROMPT = """
    Game Rules:
    Two players, Player 1 (Ally) and Player 2 (Enemy) are competing to eliminate all opposing enemy units in a Real Time Strategy (RTS) game.
    Each step, each player can assign actions to their units if they are not already doing an action. Each unit can only be assigned ONE action.
    Players can only assign actions to their ally units.
    There are 6 available actions:
    - move((Target_x, Target_y)): Unit will move to target location.
    - train(Unit_Type): Unit will train the provided unit type (only bases and barracks can use this action).
    - build((Target_x, Target_y), Building_Type): Unit will build the provided building type at the target location, consuming the resource cost from the ally base (only workers can use this action).
    - harvest((Resource_x, Resource_y), (Ally_Base_x, Ally_Base_y)): Unit will navigate to the target resource, collect a resource and bring it back to the target ally base.
    - attack((Enemy_x, Enemy_y)): Unit will navigate to, and attack the target enemy.
    - idle(): The target unit will do nothing for a round. This is the default for all available units that are not assigned an action.
    The game is over once all units and buildings from either team are killed or destroyed, the remaining team is the winner. BUILD A BARRACKS!

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

    Move format:
    Return a list of actions to take for each available unit or building in the following format:
    (<X>, <Y>): <Unit Type> <Action>(<Action Arguments>)
    (<X>, <Y>): <Unit Type> <Action>(<Action Arguments>)
    etc ...""";

    Random r = new Random();
    protected UnitTypeTable utt;
    UnitType resourceType;
    UnitType workerType;
    UnitType lightType;
    UnitType heavyType;
    UnitType rangedType;
    UnitType baseType;
    UnitType barracksType;
    File promptFile;
    File formatFile;
    File moveHistoryFile = new File(MOVE_HISTORY_PATH);

    public LLM_DeepseekR1(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());

        try {
            File dir = new File(PROJECT_PATH);
            promptFile = File.createTempFile("prompt", null, dir);
            formatFile = File.createTempFile("format", null, dir);

            String jsonContent = MOVE_RESPONSE_SCHEMA.toString();

            Files.write(formatFile.toPath(), jsonContent.getBytes(StandardCharsets.UTF_8));
 
            // System.out.println("Temp Files created: " + promptFile.toPath() + " " + formatFile.toPath());

            // Add files to command
            command += " " + promptFile.getAbsolutePath() + " " + formatFile.getAbsolutePath();

            promptFile.deleteOnExit();
            formatFile.deleteOnExit();
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }
    
    public LLM_DeepseekR1(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        reset(a_utt);
    }

    public void reset() {
    	super.reset();
        TIME_BUDGET = -1;
        ITERATIONS_BUDGET = -1;
    }
    
    public void reset(UnitTypeTable a_utt)  
    {
        utt = a_utt;
        resourceType = utt.getUnitType("Resource");
        workerType = utt.getUnitType("Worker");
        lightType = utt.getUnitType("Light");
        heavyType = utt.getUnitType("Heavy");
        rangedType = utt.getUnitType("Ranged");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
    }   
    

    public AI clone() {
        return new LLM_DeepseekR1(utt, pf);
    }

    public PlayerAction getAction(int player, GameState gs) {
        // Units are told to continue their abstraction actions until the LLM issues new ones
        if (gs.getTime() % LLM_INTERVAL != 0) {
            return translateActions(player, gs);
        }
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int width = pgs.getWidth();
        int height = pgs.getHeight();
        Player p = gs.getPlayer(player);

        // IF AN ABSTRACT ACTION IS ISSUED, UNITS WILL KEEP DOING THAT ACTION UNTIL:
        // - THE ACTION IS FINISHED
        // - THEY DIE
        // - IDLE IS CALLED ON UNIT

        ArrayList<String> features = new ArrayList<>();

        int maxActions = 0;

        for (Unit u : pgs.getUnits()) {
            if (u.getPlayer() == player) { maxActions++; }

            String unitStats;
            UnitAction unitAction = gs.getUnitAction(u);
            String unitActionString = unitActionToString(unitAction);

            String unitType;

            if (u.getType() == resourceType) {
                unitType = "Resource Node";
                unitStats = "{resources=" + u.getResources() + "}";
            } else if (u.getType() == baseType) {
                unitType = "Base Unit";
                unitStats = "{resources=" + p.getResources() + ", current_action=\"" + unitActionString + "\", HP=" + u.getHitPoints() + "}";
            } else if (u.getType() == barracksType) {
                unitType = "Barracks Unit";
                unitStats = "{current_action=\"" + unitActionString + "\", HP=" + u.getHitPoints() + "}";
            } else if (u.getType() == workerType) {
                unitType = "Worker Unit";
                unitStats = "{current_action=\"" + unitActionString + "\", HP=" + u.getHitPoints() + "}";
            } else if (u.getType() == lightType) {
                unitType = "Light Unit";
                unitStats = "{current_action=\"" + unitActionString + "\", HP=" + u.getHitPoints() + "}";
            } else if (u.getType() == heavyType) {
                unitType = "Heavy Unit";
                unitStats = "{current_action=\"" + unitActionString + "\", HP=" + u.getHitPoints() + "}";
            } else if (u.getType() == rangedType) {
                unitType = "Ranged Unit";
                unitStats = "{current_action=\"" + unitActionString + "\", HP=" + u.getHitPoints() + "}";
            } else {
                unitType = "Unknown";
                unitStats = "{}";
            }

            String unitPos = "(" + u.getX() + ", " + u.getY() + ")";
            String team = u.getPlayer() == player ? "Ally" : "Enemy";
            if (u.getType() == resourceType) { team = "Neutral"; }
            
            features.add(unitPos + " " + team + " " + unitType + " " + unitStats);
        }

        // Map size neccessary to inform LLM of movement restrictions
        String mapPrompt = "Map size: " + width + "x" + height;

        // Inclusion of turn number provides LLM with temporal context (depends if chats are reused)
        String turnPrompt = "Turn: " + gs.getTime() + "/" + 5000;

        // Helps prevent LLM from issuing more commands than units available
        String maxActionsPrompt = "Max actions: " + maxActions;

        // Opted to include a list of feature locations instead of a 2D array, because LLMs suffer with spatial inputs.
        // This excludes information like empty tiles which don't constitute much information.
        String featuresPrompt = "Feature locations:\n" + String.join("\n", features);

        String finalPrompt = PROMPT + "\n\n" + mapPrompt + "\n" + turnPrompt + "\n" + maxActionsPrompt + "\n\n" + featuresPrompt + "\n";
        // System.out.println(finalPrompt);
        String response = prompt(finalPrompt);
        logMoves(gs.getTime(), response);
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        // Parse moves
        // System.out.println(response);

        // Loop through the response and handle each move
        for (JsonElement moveElement : jsonResponse) {
            JsonObject move = moveElement.getAsJsonObject();
            JsonArray unitPosition = move.getAsJsonArray("unit_position");

            // Retrieve the unit based on position
            int unitX = unitPosition.get(0).getAsInt();
            int unitY = unitPosition.get(1).getAsInt();
            Unit unit = pgs.getUnitAt(unitX, unitY);

            if (unit.getPlayer() != player) {
                System.out.println("Cannot issue action to neutral/enemy unit ("+unitX+", "+unitY+")");
                continue;
            }

            String actionType = move.get("action_type").getAsString();
            String unitType = move.get("unit_type").getAsString();

            String rawMove = move.get("raw_move").getAsString();
            Pattern pattern;
            Matcher matcher;

            // Handle each action type
            switch (actionType) {
                case "move":
                    if (unit.getType() == baseType || unit.getType() == barracksType) {
                        System.out.println("'move' failed because unit ("+unitX+", "+unitY+") is a base or barracks");
                    }

                    pattern = Pattern.compile("\\(\\s*\\d+,\\s*\\d+\\):.*?move\\(\\(\\s*(\\d+),\\s*(\\d+)\\s*\\)\\)");
                    matcher = pattern.matcher(rawMove);

                    if (matcher.find()) {
                        int targetX = Integer.parseInt(matcher.group(1));
                        int targetY = Integer.parseInt(matcher.group(2));

                        move(unit, targetX, targetY);
                    } else {
                        System.out.println("'move' regex failed to match for raw_move: " + rawMove);
                    }

                    break;

                case "harvest":
                    if (unit.getType() != workerType) {
                        System.out.println("'harvest' failed because unit ("+unitX+", "+unitY+") is not a worker");
                    }
                    // Parse the resource position and ally base position for harvest action

                    pattern = Pattern.compile("\\(\\s*\\d+,\\s*\\d+\\):.*?harvest\\(\\((\\d+),\\s*(\\d+)\\),\\s*\\((\\d+),\\s*(\\d+)\\)\\)");
                    matcher = pattern.matcher(rawMove);

                    if (matcher.find()) {
                        int resourceX = Integer.parseInt(matcher.group(1));
                        int resourceY = Integer.parseInt(matcher.group(2));
                        Unit resourceUnit = pgs.getUnitAt(resourceX, resourceY);
                        int baseX = Integer.parseInt(matcher.group(3));
                        int baseY = Integer.parseInt(matcher.group(4));
                        Unit baseUnit = pgs.getUnitAt(baseX, baseY);

                        if (resourceUnit != null && baseUnit != null) {
                            harvest(unit, resourceUnit, baseUnit);
                        }
                    } else {
                        System.out.println("'harvest' regex failed to match for raw_move: " + rawMove);
                    }

                    break;

                case "train":
                    if (unit.getType() != baseType && unit.getType() != barracksType) {
                        System.out.println("'train' failed because unit ("+unitX+", "+unitY+") is not a base or barracks");
                    }

                    pattern = Pattern.compile("\\(\\s*\\d+,\\s*\\d+\\):.*?train\\(\\s*['\"]?(\\w+)['\"]?\\s*\\)");
                    matcher = pattern.matcher(rawMove);

                    if (matcher.find()) {
                        String stringTrainUnitType = matcher.group(1);
                        UnitType trainUnitType = stringToUnitType(stringTrainUnitType);
                        train(unit, trainUnitType);
                    } else {
                        System.out.println("'train' regex failed to match for raw_move: " + rawMove);
                    }
                    break;

                case "build":
                    if (unit.getType() != workerType) {
                        System.out.println("'build' failed because unit ("+unitX+", "+unitY+") is not a worker");
                    }

                    pattern = Pattern.compile("\\(\\s*\\d+,\\s*\\d+\\):.*?build\\(\\s*\\(\\s*(\\d+),\\s*(\\d+)\\s*\\),\\s*['\"]?(\\w+)['\"]?\\s*\\)");
                    matcher = pattern.matcher(rawMove);

                    if (matcher.find()) {
                        int buildX = Integer.parseInt(matcher.group(1));
                        int buildY = Integer.parseInt(matcher.group(2));
                        String stringBuildUnitType = matcher.group(3);
                        UnitType unitBuildType = stringToUnitType(stringBuildUnitType);
                        build(unit, unitBuildType, buildX, buildY);
                    } else {
                        System.out.println("'build' regex failed to match for raw_move: " + rawMove);
                    }

                    break;

                case "attack":
                    // Parse the target enemy position for the attack action
                    pattern = Pattern.compile("\\(\\s*\\d+,\\s*\\d+\\):.*?attack\\(\\s*\\(\\s*(\\d+),\\s*(\\d+)\\s*\\)\\s*\\)");
                    matcher = pattern.matcher(rawMove);

                    if (matcher.find()) {
                        int enemyX = Integer.parseInt(matcher.group(1));
                        int enemyY = Integer.parseInt(matcher.group(2));
                        Unit enemyUnit = pgs.getUnitAt(enemyX, enemyY);

                        if (enemyUnit != null) {
                            attack(unit, enemyUnit);
                        }
                    } else {
                        System.out.println("'attack' regex failed to match for raw_move: " + rawMove);
                    }

                    break;

                case "idle":
                    idle(unit);
                    break;

                default:
                    System.out.println("Unknown action type: " + actionType);
                    break;
            }
        }

        // The LLM struggles to attack enemy units because it can only issue actions on intervals so-
        // if any unit is in danger, it will automatically attack the nearest enemy.
        // This behavior is default for many other hard-coded bots, and is just another abstraction we-
        // will make the the LLM.
        // This overrides the existing action if the unit was issued one
        for (Unit u1 : pgs.getUnits()) {
            Unit closestEnemy = null;
            int closestDistance = 0;
            if (u1.getPlayer() != player && u1.getType().canAttack) { continue; }

            for (Unit u2 : pgs.getUnits()) {
                if (u2.getPlayer() == player) { continue; }

                int d = Math.abs(u2.getX() - u1.getX()) + Math.abs(u2.getY() - u1.getY());
                if (closestEnemy == null || d < closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }

            if (closestEnemy != null && closestDistance == 1) {
                System.out.println("Attacking nearest unit!");
                attack(u1, closestEnemy);
            }
        }

        // This method simply takes all the unit actions executed so far, and packages them into a PlayerAction
        return translateActions(player, gs);
    }

    // Abstraction functions:
    // - move(Unit ally, int x, int y)
    // - train(Unit ally, UnitType type)
    // - build(Unit ally, UnitType building, int x, int y)
    // - harvest(Unit ally, Unit resource, Unit base)
    // - attack(Unit ally, Unit enemy)
    // - idle(Unit u)
    // - buildIfNotAlreadyBuilding(Unit ally, UnitType building, Int x, Int y, Player p, PhysicalGameState pgs) (This function has been omitted from the LLM)

    public String prompt(String prompt) {
        try {
            Files.write(promptFile.toPath(), prompt.getBytes(StandardCharsets.UTF_8));

            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));

            // Start the process
            Process process = processBuilder.start();
            // process.getErrorStream().transferTo(System.out); // Debugging

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("sbatch command failed with exit code: " + exitCode);
                return "[]";
            }
            
            String outputFilePath = PROJECT_PATH + "/output.out";
            String jsonContent = new String(Files.readAllBytes(Paths.get(outputFilePath)), StandardCharsets.UTF_8);

            // Parse JSON to extract "response"
            JsonParser parser = new JsonParser();
            JsonObject jsonObject = parser.parse(jsonContent).getAsJsonObject();
            String response = jsonObject.get("response").getAsString();

            return response;

        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
            return "[]";
        }
    }

    public void logMoves(int turn, String moves) {
        try {
            JsonObject root;
            JsonParser parser = new JsonParser();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            // Load existing file if it exists, otherwise create a new object
            if (moveHistoryFile.exists()) {
                String existing = Files.readString(moveHistoryFile.toPath(), StandardCharsets.UTF_8);
                root = parser.parse(existing).getAsJsonObject();
            } else {
                root = new JsonObject();
            }

            // Store the moves under the turn number as key
            root.addProperty(String.valueOf(turn), moves);

            // Write updated content back to file
            Files.write(
                moveHistoryFile.toPath(),
                gson.toJson(root).getBytes(StandardCharsets.UTF_8)
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to log moves", e);
        }
    }

    @Override
    public List<ParameterSpecification> getParameters()
    {
        List<ParameterSpecification> parameters = new ArrayList<>();
        
        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));

        return parameters;
    }

    private UnitType stringToUnitType(String string) {
        string = string.toLowerCase();
        switch (string) {
            case "worker":
                return workerType;
            case "light":
                return lightType;
            case "heavy":
                return heavyType;
            case "ranged":
                return rangedType;
            case "base":
                return baseType;
            case "barracks":
                return barracksType;
            default:
                System.out.println("Unknown unit type: " + string);
                return workerType;
        }
    }

    private String unitActionToString(UnitAction action) {
        if (action == null) { return "idling"; }

        String description;
        switch (action.getType()) {
            case UnitAction.TYPE_MOVE:
                description = String.format("moving to (%d,%d)", action.getLocationX(), action.getLocationY());
                break;
            case UnitAction.TYPE_HARVEST:
                description = String.format("harvesting from (%d,%d)", action.getLocationX(), action.getLocationY());
                break;
            case UnitAction.TYPE_RETURN:
                description = String.format("returning resources to (%d,%d)", action.getLocationX(), action.getLocationY());
                break;
            case UnitAction.TYPE_PRODUCE:
                description = String.format("producing unit at (%d,%d)", action.getLocationX(), action.getLocationY());
                break;
            case UnitAction.TYPE_ATTACK_LOCATION:
                description = String.format("attacking location (%d,%d)", action.getLocationX(), action.getLocationY());
                break;
            case UnitAction.TYPE_NONE:
                description = "idling";
                break;
            default:
                description = "unknown action";
                break;
        }
        return description;
    }
}
