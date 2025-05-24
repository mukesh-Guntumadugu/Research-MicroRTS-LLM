package ai.abstraction;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.ParameterSpecification;
import java.util.ArrayList;
import java.util.LinkedList; // why are we not using
import java.util.List;
import java.util.Random;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import com.google.gson.*;
import rts.GameState;
import rts.PhysicalGameState;
import rts.UnitAction;
import rts.Player;
import rts.PlayerAction;
import rts.units.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.util.Date;



/**
 *
 * @author Brendan Smyers
 */
public class LLM_Gemini extends AbstractionLayerAI {

    /**
     * Static & non-static variables
     * connected to 2 classes  unitTypeTable & unitType
     */

    // NOTE: TESTING ONLY gmu3r2g need to remove it are better ways to handile it in github
    static final String API_KEY = "AIzaSyC5g16BvYlLS79lJJCk7C_H8bn4EIcJzr4";//"AIzaSyC5g16BvYlLS79lJJCk7C_H8bn4EIcJzr4";// "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"; // "AIzaSyC5g16BvYlLS79lJJCk7C_H8bn4EIcJzr4"; // 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
    // Supported models: AIzaSyC5g16BvYlLS79lJJCk7C_H8bn4EIcJzr4
    // gemini-1.5-flash (15 req/min)
    // gemini-2.0-flash (15 req/min)
    String MODEL = "gemini-2.0-flash";
    static final String ENDPOINT_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    static final JsonObject MOVE_RESPONSE_SCHEMA;
    // How often the LLM should act on the game state
    // More frequent LLM intervention is not necessarily better
    // Low = more frequent, higher = less freqeunt
    static final Integer LLM_INTERVAL = 2;  // ? why can't i have less than that
    LocalDateTime now = LocalDateTime.now();
    String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
    JsonObject wrapper = new JsonObject();
    String FilenameXXXten ="";
    Random r = new Random();
    protected UnitTypeTable utt; // different class
    UnitType resourceType;
    UnitType workerType;
    UnitType lightType;
    UnitType heavyType;
    UnitType rangedType;
    UnitType baseType;
    UnitType barracksType;
    int totalMovesGenerated = 0;
    int totalMovesAccepted = 0;
    int totalMovesRejected = 0;
    int promptstart =0;

    // is there any other way to give prompt in a better way to give Free to it ?

    /**
     * prompt that needs to change based on they model
     *
     * V1: Game Rules:
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
     *
     Unit types:
     | Unit Type | HP | Cost | Attack Damage | Attack Range | Speed | Abilities                                                       |
     |-----------|----|------|---------------|--------------|-------|-----------------------------------------------------------------|
     | worker    | 1  | 1    | 1             | 1            | 1     | Trained from base, Gathers resources, builds base and barracks  |
     | light     | 4  | 2    | 2             | 1            | 2     | Trained from barracks, High Speed                               |
     | heavy     | 8  | 3    | 4             | 1            | 1     | Trained from barracks, High HP, High Damage                     |
     | ranged    | 3  | 2    | 1             | 3            | 1     | Trained from barracks, High Range                               |
     *
     Building types:
     | Building Type | HP  | Cost | Abilities                               |
     |---------------|-----|------|-----------------------------------------|
     | base          | 10  | 10   | Produces workers, Stores resources      |
     | barracks      | 5   | 5    | Produces Light, Heavy, and Ranged units |
     *
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
     *
     Game state format:
     The game state consists the map size and a list of feature locations (zero-indexed) within the the map bounds. Units and buildings have different properties associated with their current state. All units and buildings (except resources) have an 'available' property. If a unit or building is available an action issued to it will be accepted.
     *
     Move format:
     Return a list of actions to take for each available unit or building in the following format:
     (<X>, <Y>): <Unit Type> <Action>(<Action Arguments>)
     (<X>, <Y>): <Unit Type> <Action>(<Action Arguments>)
     etc ..."""
     *
     */
    String PROMPT = """
          Strategy Game Instructions
                  You are controlling a strategy game. Your job is to issue the correct actions for each of your units and buildings in every turn.
            
                  🎯 Strategy Objective
                  Always assign one worker to perform the harvest action (keep gathering resources).
            
                  All other workers and combat units must attack the enemy — go fully aggressive (all-in).
            
                  Your base should keep training new workers if you have resources.
            
                  As soon as a barracks is available, start training combat units (light, heavy, ranged) and send them to attack.
            
                  🛠️ Available Actions
                  move((x, y)): Move the unit to a specified location
            
                  attack((enemy_x, enemy_y)): Attack the enemy unit or building at given position
            
                  harvest((resource_x, resource_y), (base_x, base_y)): Worker will collect resource and return it to base
            
                  build((x, y), building_type): Worker will construct a base or barracks at given location
            
                  train(unit_type): Base or barracks will produce a unit
            
                  🪓 Harvesting Rules
                  Use this format:
                  harvest((resource_x, resource_y), (base_x, base_y))
            
                  The game engine handles all the logic — moving to the resource, harvesting, moving to the base, and returning it.
            
                  You should NOT manually give move, harvest, and return separately.
            
                  Just issue harvest(...) once and the worker will follow the correct sequence.
            
                  Example:
            
                  (2, 0): worker harvest((0, 0), (2, 1))
                  ⚔️ All-In Attack Instructions
                  All other units (including idle workers, light, heavy, and ranged units) must attack the closest enemy.
            
                  You can issue:
            
                  
                  (x, y): unit_type attack((enemy_x, enemy_y))
                  Example:
                  (1, 0): worker attack((5, 6))
                  (3, 1): light attack((6, 5))
                  ✅ Summary
                  Only one worker should harvest every turn.
            
                  All other units should attack the enemy as fast as possible.
            
                  Base keeps training new workers.
            
                  Prioritize aggression after securing 1 harvester and the base needs to produse all the workers to attack the enemy .
            
    """;
    /*
    * 1. Early Game - Economy Focus
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
            * */

    /**
     * starts from hear basically before main method this one will have more priority
     */



    /**
     *
     *
     * Json retalted static block like structure and elements are over hear.
     * */
    static { // first priority when calling a class before main() & constructor
        MOVE_RESPONSE_SCHEMA = new JsonObject();

        String schemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "thinking": {
                      "type": "string",
                      "description": "Plan out what moves you should take you can do multiple moves at a times"
                    },
                    "moves": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "raw_move": {
                            "type": "string"
                          },
                          "unit_position": {
                            "type": "array",
                            "items": {
                              "type": "integer"
                            },
                            "minItems": 2,
                            "maxItems": 2
                          },
                          "unit_type": {
                            "type": "string",
                            "enum": [
                              "worker",
                              "light",
                              "heavy",
                              "ranged",
                              "base",
                              "barracks"
                            ]
                          },
                          "action_type": {
                            "type": "string",
                            "enum": [
                              "move",
                              "train",
                              "build",
                              "harvest",
                              "attack"
                            ]
                          }
                        },
                        "required": [
                          "raw_move",
                          "unit_position",
                          "unit_type",
                          "action_type"
                        ]
                      }
                    }
                  },
                  "required": [
                    "moves",
                    "thinking"
                  ],
                  "propertyOrdering": [
                    "thinking",
                    "moves"
                  ]
                }
      """; // "thinking",

        JsonParser parser = new JsonParser();   /// if any format of json issue take a look and any modifications have a look
        JsonObject responseSchema = parser.parse(schemaJson).getAsJsonObject();
        MOVE_RESPONSE_SCHEMA.add("response_schema", responseSchema);

        System.out.println("responseSchema  270 :  gmu3r2g  -> "+responseSchema);

    }

  // is there any other way to give prompt in a better way to give Free to it ?


    /**
     * constructors
     */

    /**
     *
     * @param a_utt
     *
     */
    public LLM_Gemini(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding()); //
        System.out.println(" in this 1 st nd mg546924 288   ----- >  Y / N ");
        String timestamp1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        FilenameXXXten = "LLmGemini_"+timestamp1+".json";
        // this is also good i gess

    }

    /**
     *
     * @param a_utt = ?
     * @param a_pf = ?
     */
    public LLM_Gemini(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf); //
        System.out.println(" in this 2 nd mg546924 180 "+ a_utt);
        reset(a_utt); // method call
    }


    /**
     * Methods
     */

    /**
     * @method :  logEndGameMetrics
     *
     * head a issue check it will work properly are not write now we are adding json responses
     * this format is bad need to update
     *
     *
     *
     *
     */
    private void logEndGameMetrics() {
        System.out.println(" 158 check gmu3r2g");

        JsonObject metrics = new JsonObject();
        metrics.addProperty("moves_generated", totalMovesGenerated);
        metrics.addProperty("moves_accepted", totalMovesAccepted);
        metrics.addProperty("moves_rejected", totalMovesRejected);

        JsonObject totals = new JsonObject();
        totals.addProperty("total_moves_generated", totalMovesGenerated);
        totals.addProperty("total_moves_accepted", totalMovesAccepted);
        totals.addProperty("total_moves_rejected", totalMovesRejected);

        JsonObject wrapper = new JsonObject();
        wrapper.add("player_final_stats", metrics);
        wrapper.add("game_metrics", totals);

        // ✅ Add end timestamp
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
        wrapper.addProperty("end_time", timestamp);

        try (FileWriter writer = new FileWriter("game_summary.json", true)) {
            System.out.println(" am i in : 245 LLM _gemini ");
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(wrapper));
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *
     * reset function is reseting they Time budget & Iteration budget
     * going to they reset of abstract layer ai to reset are clearing they data from
     * the hashMap   HashMap<Unit, AbstractAction>
     *
     *
     * TIME_BUDGET  in  aiwithComputationbudget
     * ITERATIONS_BUDGET  in  aiwithComputationbudget
     */
    public void reset() {
    	super.reset();
        TIME_BUDGET = -1;
        ITERATIONS_BUDGET = -1;
    }

    /**
     *
     * @param a_utt
     */
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

    /**
     * utt passing with a_utt
     * pf from abstract layer ai
     *
     *
     * @return
     */

    @Override
    public AI clone() {
        return new LLM_Gemini(utt, pf);
    }


    /**
     *
     * @param player ID of the player to move. Use it to check whether units are yours or enemy's
     * @param gs the game state where the action should be performed
     * @return
     */
    @Override
    public PlayerAction getAction(int player, GameState gs) {

        String finalPrompt;
        System.out.println(" in line number 222 gmu3r2g ");
        // Units are told to continue their abstraction actions until the LLM issues new ones
        if (gs.getTime() % LLM_INTERVAL != 0) {
            if (gs.gameover()) {
                logEndGameMetrics();
            }
            // remove this  from heare
            PlayerAction pa = translateActions(player, gs);
            System.out.println("🎯406  translateActions() generated PlayerAction:");
            System.out.println(pa);
            return pa;
            // till heare
           // return translateActions(player, gs); need to add
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

        System.out.println(" 469  ----------------------------------- \n featuresPrompt :  ");
        System.out.println(featuresPrompt);
        System.out.println(" ----------------------------------- ");


        // need to do in other way as off now llm
        // prompt will be passed only once so that we can save tokens
        if(promptstart == 0) {
             finalPrompt = PROMPT + "\n\n" + mapPrompt + "\n" + turnPrompt + "\n" + maxActionsPrompt + "\n\n" + featuresPrompt + "\n";
            promptstart = promptstart+1;
        }
        else{
             finalPrompt =  mapPrompt + "\n" + turnPrompt + "\n" + maxActionsPrompt + "\n\n" + featuresPrompt + "\n";
        }

        //  need to implement they  Context caching process


        finalPrompt = PROMPT + "\n\n" + mapPrompt + "\n" + turnPrompt + "\n" + maxActionsPrompt + "\n\n" + featuresPrompt + "\n";


        // System.out.println(finalPrompt);
        // Prompt gemini
        String response = prompt(finalPrompt);
        System.out.println(" 476  ----------------------------------- ");

        System.out.println(response);
        System.out.println(" 479   ----------------------------------- ");
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String prettyJson = gson.toJson(jsonResponse);

        try (FileWriter file = new FileWriter(FilenameXXXten, true)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            file.write("[" + timestamp + "]\n");
            file.write(prettyJson);
            file.write(System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonArray moveElements = jsonResponse.getAsJsonArray("moves");

        // Parse moves

        // Loop through the response and handle each move
        for (JsonElement moveElement : moveElements) {
            JsonObject move = moveElement.getAsJsonObject();
            JsonArray unitPosition = move.getAsJsonArray("unit_position");

            // Retrieve the unit based on position
            int unitX = unitPosition.get(0).getAsInt();
            int unitY = unitPosition.get(1).getAsInt();
            Unit unit = pgs.getUnitAt(unitX, unitY);

            if (unit == null) {
                System.out.println(" ❌ No unit found at position (" + unitX + ", " + unitY + ")   --------------- investigate as json fail are 1 move is fail ");
                continue; // Skip this move
            }

            if (unit.getPlayer() != player) {
                System.out.println("Cannot issue action to neutral/enemy unit ("+unitX+", "+unitY+")");
                continue;
            }

            String actionType = move.get("action_type").getAsString();
            String unitType = move.get("unit_type").getAsString();

            String rawMove = move.get("raw_move").getAsString();
            Pattern pattern;
            Matcher matcher;



            if (unit == null) {
                System.out.println("Action failed: No unit found at position (" + unitX + ", " + unitY + ")");
                break;
            }


            System.out.println("  LLM  532 ---------------------   ");
            System.out.println(" 556 -  : -> "+actionType);
            System.out.println("  ---------------------  start of switch  ");
            // Handle each action type
            switch (actionType) {
                case "move":
                    System.out.println(" gmu3r2g 561  move   : --- gm1 ");
                    System.out.println("  unit.getType()  : " + unit.getType()+" : baseType :  "+baseType+" barracksType "+barracksType);
                    if (unit.getType() == baseType || unit.getType() == barracksType) {
                        System.out.println("'move' failed because unit ("+unitX+", "+unitY+") is a base or barracks");
                    }

                    pattern = Pattern.compile("\\(\\s*\\d+,\\s*\\d+\\):.*?move\\(\\(\\s*(\\d+),\\s*(\\d+)\\s*\\)\\)");
                    matcher = pattern.matcher(rawMove);
                    System.out.println(" pattern :  "+pattern+"  matcher : "+matcher);

                    if (matcher.find()) {
                        int targetX = Integer.parseInt(matcher.group(1));
                        int targetY = Integer.parseInt(matcher.group(2));
                        System.out.println(" targetx :  "+targetX+"  targetY : "+targetY);


                        move(unit, targetX, targetY);
                    } else {
                        System.out.println("'move' regex failed to match for raw_move: " + rawMove);
                    }
                    if (gs.gameover()) {
                        logEndGameMetrics();
                    }

                    break;

                case "harvest":

                    System.out.println(" gmu3r2g 589   harvest    : --- gm2 ");
                    System.out.println("  unit.getType()  : " + unit.getType().name+" : baseType :  "+workerType.name);

                    if (unit.getType() != workerType) {
                        System.out.println("'harvest' failed because unit ("+unitX+", "+unitY+") is not a worker");
                    }
                    // Parse the resource position and ally base position for harvest action

                    pattern = Pattern.compile("\\(\\s*\\d+,\\s*\\d+\\):.*?harvest\\(\\((\\d+),\\s*(\\d+)\\),\\s*\\((\\d+),\\s*(\\d+)\\)\\)");
                    matcher = pattern.matcher(rawMove);

                    System.out.println(" pattern :  "+pattern+" :  matcher : "+matcher);
                    System.out.println(" gmu3r2g 604  train  end   : --- ");

                    System.out.println(" gmu3r2g 590  harvest  : --- ");
                    if (matcher.find()) {
                        System.out.println(" matcher 605 true  ");
                        int resourceX = Integer.parseInt(matcher.group(1));
                        int resourceY = Integer.parseInt(matcher.group(2));
                        System.out.println(" resourceX"+resourceX); // 0
                        System.out.println(" resourceY"+resourceY);  //0
                        Unit resourceUnit = pgs.getUnitAt(resourceX, resourceY);

                        int baseX = Integer.parseInt(matcher.group(3));
                        int baseY = Integer.parseInt(matcher.group(4));
                        System.out.println(" baseX"+baseX); // 2
                        System.out.println(" baseY"+baseY); // 1

                        Unit baseUnit = pgs.getUnitAt(baseX, baseY);

                        if (resourceUnit != null && baseUnit != null) {
                            System.out.println("  --->  inside 620 which means able to how  resourses and base is located ");
                            System.out.println("unit type: " + unit.getType().name);
                            System.out.println("resource type: " + resourceUnit.getType().name);
                            System.out.println("base type: " + baseUnit.getType().name);
                            harvest(unit, resourceUnit, baseUnit);
                            System.out.println("unit type: " + unit.getType().name);
                            System.out.println("resource type: " + resourceUnit.getType().name);
                            System.out.println("base type: " + baseUnit.getType().name);
                        }
                    } else {
                        System.out.println("'harvest' regex failed to match for raw_move: " + rawMove);
                    }
                    if (gs.gameover()) {
                        logEndGameMetrics();
                    }

                    break;

                case "train":

                    System.out.println(" gmu3r2g 604  train   : ---  gm3 ");
                    System.out.println("  unit.getType()  : " + unit.getType().name+" : baseType :  "+baseType.name);

                    System.out.println(" unit.getType()  "+unit.getType().name+" :  +barracksType:  "+barracksType.name);
                    System.out.println(" gmu3r2g 604  train  end   : --- ");

                    if ((unit.getType() != baseType) && (unit.getType() != barracksType)) {
                        System.out.println("'train' failed because unit ("+unitX+", "+unitY+") is not a base or barracks");
                    }

                    pattern = Pattern.compile("\\(\\s*\\d+,\\s*\\d+\\):.*?train\\(\\s*['\"]?(\\w+)['\"]?\\s*\\)");
                    matcher = pattern.matcher(rawMove);
                    System.out.println(" 645 : pattern : "+pattern+" : matcher"+matcher);

                    if (matcher.find()) {
                        String stringTrainUnitType = matcher.group(1);
                        System.out.println(" stringTrainUnitType  = "+stringTrainUnitType);
                        UnitType trainUnitType = stringToUnitType(stringTrainUnitType);
                        train(unit, trainUnitType);
                    } else {
                        System.out.println("'train' regex failed to match for raw_move: " + rawMove);
                    }
                    if (gs.gameover()) {
                        logEndGameMetrics();
                    }
                    break;

                case "build":
                    System.out.println(" gmu3r2g 662  build   : --- gm4 ");
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
                    if (gs.gameover()) {
                        logEndGameMetrics();
                    }

                    break;

                case "attack":
                    System.out.println(" gmu3r2g 662  attack   : --------  gm5");
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
                    if (gs.gameover()) {
                        logEndGameMetrics();
                    }

                    break;

                case "idle":
                    System.out.println(" gmu3r2g 709  idle   : --------  gm6");

                    idle(unit);
                    if (gs.gameover()) {
                        logEndGameMetrics();
                    }
                    break;

                default:
                    System.out.println(" gmu3r2g 718  default   : --------   gm99 ");
                    System.out.println("Unknown action type: " + actionType);
                    if (gs.gameover()) {
                        logEndGameMetrics();
                    }
                    break;
            }
            System.out.println("  ---------------------  end of switch  ");
        }

        // The LLM struggles to attack enemy units because it can only issue actions on intervals so-
        // if any unit is in danger, it will automatically attack the nearest enemy.
        // This behavior is default for many other hard-coded bots, and is just another abstraction we-
        // will make the the LLM.
        // This overrides the existing action if the unit was issued one

        /** v2 it never strugule to attack it was overriding  in the middle some where in the process
         *
         */

        for (Unit u1 : pgs.getUnits()) {
            Unit closestEnemy = null;
            int closestDistance = 0;
            System.out.println(" u1.getPlayer() : "+u1.getPlayer()+" player : "+player+" u1.getType().canAttack"+u1.getType().canAttack);
            if ((u1.getPlayer() != player) && u1.getType().canAttack) { continue; }

            for (Unit u2 : pgs.getUnits()) {
                System.out.println("  u2.getPlayer() "+u2.getPlayer()+" player :"+player);
                if (u2.getPlayer() == player) { continue; }

                int d = Math.abs(u2.getX() - u1.getX()) + Math.abs(u2.getY() - u1.getY());
                System.out.println(" d : "+d+" closestEnemy "+closestEnemy+" closestDistance+ :"+closestDistance+" u2 :"+u2+" d : "+d);
                if (closestEnemy == null || d < closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
            /** this is in previous that's over riding all the harvest / attack , move
             if (closestEnemy != null && closestDistance == 1) {
             System.out.println("Attacking nearest unit!");
             attack(u1, closestEnemy); // w going on there
             } */
            if (closestEnemy != null && closestDistance == 1) {
                if (getAbstractAction(u1) == null) { // ✅ Only attack if no action was set by LLM
                    System.out.println("Attacking nearest unit!");
                    attack(u1, closestEnemy);
                } else {
                    System.out.println("⚠️ Skipping override for " + u1 + " (already has action)");
                }
            }

        }

        totalMovesGenerated++; // count
        totalMovesAccepted++;   // count
        // This method simply takes all the unit actions executed so far, and packages them into a PlayerAction
        System.out.println(" gs.gameover() 506 gmu3r2g "+gs.gameover());
        Player p3 = gs.getPlayer(player);
        System.out.println("Running getAction for Player: 508  " + player);
        int currentTime = gs.getTime();
        Player p0 = gs.getPlayer(0);
        Player p1 = gs.getPlayer(1);

// Score can be estimated using evaluation (if used), but usually this prints resource values or utility
        System.out.printf("T: %d, P0: %d (%s), P1: %d (%s)%n",
                currentTime,
                p0.getID(), p0.getResources(),  // or any evaluation function result
                p1.getID(), p1.getResources()
        );
        if (gs.gameover()) {
            logEndGameMetrics();
        }
        // remove this  from heare
        PlayerAction pa = translateActions(player, gs);
        System.out.println("🎯 788  translateActions() generated PlayerAction:");
        System.out.println(pa);
        return pa;
        // till heare
        //return translateActions(player, gs);
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
            // Create the body of the request
            JsonObject requestBody = new JsonObject();
            JsonArray contents = new JsonArray();

            JsonObject part = new JsonObject();
            part.addProperty("text", prompt);

            JsonArray parts = new JsonArray();
            parts.add(part);

            JsonObject content = new JsonObject();
            content.add("parts", parts);

            contents.add(content);
            requestBody.add("contents", contents);

            // Add the schema to generationConfig
            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("response_mime_type", "application/json");

            // Add the response schema
            generationConfig.add("response_schema", MOVE_RESPONSE_SCHEMA.get("response_schema"));

            requestBody.add("generationConfig", generationConfig);  // Add generationConfig with schema

            // Send the request
            URL url = new URL(ENDPOINT_URL + MODEL + ":generateContent?key=" + API_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            System.out.println("🔼 gmu3r2g 651 :  Sending requestBody to Gemini API:");
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(requestBody));

            System.out.println("🔼 gmu3r2g end of 651  :   requestBody to Gemini API: end -------------- ");
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Check for success (HTTP 200) or error (HTTP 400 or other)
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                // ⬇️ Print the raw JSON response string (BEFORE parsing)
               // System.out.println("✅ Raw Response JSON from Gemini: gmu3r2g ");
                System.out.println(response.toString());
                JsonParser parser1 = new JsonParser();
                JsonObject jsonResponse1 = parser1.parse(response.toString()).getAsJsonObject();

                // Optional: pretty print full response
                System.out.println("✅ Parsed JSON Response:");
                System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(jsonResponse1));

                JsonArray candidates1 = jsonResponse1.getAsJsonArray("candidates");
                JsonObject firstCandidate1 = candidates1.get(0).getAsJsonObject();
                JsonObject contentObj1 = firstCandidate1.getAsJsonObject("content");
                JsonArray partsArray1 = contentObj1.getAsJsonArray("parts");
                JsonObject firstPart1 = partsArray1.get(0).getAsJsonObject();

                System.out.println("✅ First Part Extracted:   775 gmu3r2g ");
                System.out.println("  ------------------------------------  ");
                System.out.println(firstPart1);
                System.out.println("  ------------------------------------ 784   ");

                // Process the response
                JsonParser parser = new JsonParser();
                JsonObject jsonResponse = parser.parse(response.toString()).getAsJsonObject();
                JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
                JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                JsonObject contentObj = firstCandidate.getAsJsonObject("content");
                JsonArray partsArray = contentObj.getAsJsonArray("parts");
                JsonObject firstPart = partsArray.get(0).getAsJsonObject();

               // System.out.println(firstPart);

                return firstPart.get("text").getAsString();
            } else {
                // Read the error response if not HTTP_OK
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = br.readLine()) != null) {
                    errorResponse.append(errorLine.trim());
                }

                System.out.println("Error response: " + errorResponse.toString());
                return "Error contacting Gemini API.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error contacting Gemini API.";
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
