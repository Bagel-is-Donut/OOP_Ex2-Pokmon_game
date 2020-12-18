package gameClient;

import Server.Game_Server_Ex2;
import api.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class Ex2 implements Runnable {
    private static MyFrame frame;
    private static Arena arena;

    public static void main(String[] args) {
        Thread client = new Thread(new Ex2());
        client.start();
    }

    @Override
    public void run() {
        /*
    -------------------------------------------------------------------------------------------------
    Game initializing
    -------------------------------------------------------------------------------------------------
    */
        int level_number = 0; //The level of the game [0,23]
        game_service game = Game_Server_Ex2.getServer(level_number);
        //Logging in
//        int id = 626262;
//        game.login(id);
        System.out.println(game); //Prints the server details
        String gameGraph_str = game.getGraph();
        System.out.println(gameGraph_str); //Prints the graph details
        DWGraph_Algo gameGraph = new DWGraph_Algo();
        gameGraph.load(gameGraph_str);
        init(game, gameGraph);

                /*
    -------------------------------------------------------------------------------------------------
    Game Launching
    -------------------------------------------------------------------------------------------------
    */
        game.startGame();
        frame.setTitle("Ex2 - OOP " + game.toString());
        int ind = 0;

        //Initialize an ArrayList that contains all the targeted pokemons.
        List<CL_Pokemon> targetedPokemons = new ArrayList<>();

        //Keep running while the game is on
        while (game.isRunning()) {
            int sleepTime = moveAgent(game, gameGraph.getGraph(), gameGraph, targetedPokemons);
            System.out.println("sleepTime: " + sleepTime);
            try {
                if (ind % 1 == 0)
                    frame.repaint();
                Thread.sleep(sleepTime);
                ind++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String game_str = game.toString();

        System.out.println(game_str);
        System.exit(0);
    }

    /*
    -------------------------------------------------------------------------------------------------
    Functions
    -------------------------------------------------------------------------------------------------
    */

    /**
     * The method gets a game service and initialize the graph and the agents before the game is starting
     *
     * @param game the game
     */
    private void init(game_service game, dw_graph_algorithms graph) {
        String pokemons = game.getPokemons();
        arena = new Arena();
        arena.setGraph(graph.getGraph());

        //Creates a list which will contain all the pokemons in the game.
        List<CL_Pokemon> pokemonsList = Arena.json2Pokemons(pokemons);
        arena.setPokemons(pokemonsList);
        frame = new MyFrame("OOP Ex2");
        frame.setSize(1000, 700);
        frame.update(arena);
        frame.show();
        String info = game.toString();
        JSONObject line;
        try {
            line = new JSONObject(info);
            JSONObject object = line.getJSONObject("GameServer");
            int numOfAgents = object.getInt("agents");

            /*
            Creates a priority queue which will contain all the pokemons in the game.
            The priority queue ranks the pokemons by their values from the greater to the lesser.
            */
            PriorityQueue<CL_Pokemon> pokemonsPQ = new PriorityQueue<>(new Pokimon_Comparator());

            //Moves all the pokemons from the list to the PQ
            pokemonsPQ.addAll(pokemonsList);

            /*
            Locates all the agents in the graph,
            the first agent locates in the closest node to the pokemon with the greatest value and etc.
            */
            int avgNode = graph.getGraph().edgeSize();
            for (int i = 0; i < numOfAgents; i++) {
                if (pokemonsPQ.size() > 0) {
                    CL_Pokemon currentPokemon = pokemonsPQ.poll();
                    int pokemonSrc = getPokemonSrc(currentPokemon, graph.getGraph());

                    //locates the current agent in the nearest node to the pokemon.
                    game.addAgent(pokemonSrc);
                }
                /*
                    If there are more agents than pokemons, then divides the number of nodes in the graph by 2
                    and then locates the agent in the graph.
                 */
                else {
                    avgNode = avgNode / 2;
                    game.addAgent(avgNode);
                }
            }

            //Prints the agents details
            System.out.println(game.getAgents());
        } catch (
                JSONException e) {
            e.printStackTrace();
        }

    }

    /**
     * The method gets a game and a graph and moves each of the agents along the edge,
     * in case the agent is on a node the next destination (next edge) is chosen by
     * an algorithm which find the most value pokemon in his area.
     *
     * @param game             the game
     * @param graph            the graph
     * @param ga
     * @param targetedPokemons
     * @return
     */
    private int moveAgent(game_service game, directed_weighted_graph graph, dw_graph_algorithms ga, List<CL_Pokemon> targetedPokemons) {
        int destination = 0, sleepTime = 500;
        //Creates an ArrayList which will contain the sleep time of each of the agents.
        ArrayList<Integer> sleepList = new ArrayList<>();

        // update game graph
        String updatedGraph = getUpdateGraph(game);

        // update agents list
        List<CL_Agent> newAgentsList = getUpdateAgents(updatedGraph, graph);

        // update pokemons list
        List<CL_Pokemon> newPokemonsList = getUpdatePokemons(game, graph);

        for (CL_Agent currentAgent : newAgentsList) {
            //Takes an agent from the agentList.
            //Checks if the agent is at a node, if it is gives him a new destination.
            if (currentAgent.getNextNode() == -1) {

                //Finds the nearest pokemon with the greatest value.
                CL_Pokemon target = getNearestPokemon(currentAgent, ga, targetedPokemons, newPokemonsList);

                //If all the pokemons have already been targeted, then the agent will stay at the same node
                if (target == null) {
                    printAgentMove();
                    return sleepTime;
                }

                //Finds the dest of nearest node to the target.
                int pokemon_dest = getPokemonDest(currentAgent, target, graph);
                destination = pokemon_dest;

                //Determines which node will be the next destination
                int newDest = nextNode(currentAgent, pokemon_dest, ga);

                //Sets a new destination for the current agent.
                game.chooseNextEdge(currentAgent.getID(), newDest);

                //Prints the agent move.
                printAgentMove(currentAgent, newDest, pokemon_dest, target);

                //Determines the thread sleep.
                sleepTime = getSleepTime(currentAgent, destination, ga);
                sleepList.add(sleepTime);
            }
        }
        /*
        Returns the minimum sleep time in the sleepList.
         */
        int minSleep = 500;
        for (int x : sleepList) {
            if (x < minSleep) {
                minSleep = x;
            }
        }
        return minSleep;
    }

    /**
     * The method gets a graph, an agent and a destination determines the sleep time.
     *
     * @param agent the agent
     * @param destination the destination of the agent
     * @param ga the graph
     * @return the sleep time
     */
    private int getSleepTime(CL_Agent agent, int destination, dw_graph_algorithms ga) {
        double distance = 0, edge = 0, maxSpeed = 0;
        int result;
        boolean ans = false;
        //Calculates the distance of the edges of the agent from his designation.
        distance = ga.shortestPath(agent.getSrcNode(), destination).size() - 1;

        /*
        If the agent is going to the pokemon's edge, then return zero.
        Otherwise, return 500.
         */
        if (distance <= 1) {
            maxSpeed = agent.getSpeed();
            edge = ga.shortestPathDist(agent.getSrcNode(), destination);
            ans = true;
        }

        if (ans == false) {
            result = 500;
        }
        else {
//            result = (int)((edge*10)/maxSpeed);
            result = 0;
        }
        System.out.println("distance: " + distance);
        return result;
    }

    /**
     * Prints a message if the agent did not move.
     */
    private void printAgentMove() {
        System.out.println("None of the agents moved, ");
        System.out.println("All the pokemons have already been targeted.");
    }

    /**
     * Prints the agents move (if he moved).
     *
     * @param currentAgent the agent
     * @param newDest      the new agent's distance
     */
    private void printAgentMove(CL_Agent currentAgent, int newDest, int pokemon_dest, CL_Pokemon pokemon) {
        //Agent details
        int agentID = currentAgent.getID();
        double agentValue = currentAgent.getValue();
        int agentSrc = currentAgent.getSrcNode();
        System.out.println("Agent: " + agentID + ", value: " + agentValue + " is chasing after pokemon  " + pokemon + " at node " + pokemon_dest);
        System.out.println("Agent: " + agentID + ", value: " + agentValue + " is moving from node " + agentSrc + " to node " + newDest);
    }

    /**
     * Returns the update pokemons list and set the pokemons in the arena.
     *
     * @param game  the game
     * @param graph the graph
     * @return the update pokemons list
     */
    private List<CL_Pokemon> getUpdatePokemons(game_service game, directed_weighted_graph graph) {
        String pokemons = game.getPokemons();
        List<CL_Pokemon> newPokemonsList = Arena.json2Pokemons(pokemons);
        for (CL_Pokemon currentPok : newPokemonsList) {
            Arena.updateEdge(currentPok, graph);
        }
        arena.setPokemons(newPokemonsList);
//        System.out.println("Pokemon info:" + newPokemonsList.toString());
//        System.out.println("Pokemon Edge: " + newPokemonsList.get(0).get_edge());

        return newPokemonsList;
    }

    /**
     * Returns the update agents list.
     *
     * @param updatedGraph the updated graph
     * @param graph        the graph
     * @return an update agent list
     */
    private List<CL_Agent> getUpdateAgents(String updatedGraph, directed_weighted_graph graph) {
        List<CL_Agent> newAgentsList = Arena.getAgents(updatedGraph, graph);
        arena.setAgents(newAgentsList);
        return newAgentsList;
    }

    /**
     * Gets the update graph.
     *
     * @param game the game
     * @return the update graph
     */
    private String getUpdateGraph(game_service game) {
        String updatedGraph = game.move();
        System.out.println(updatedGraph);
        return updatedGraph;
    }

    /**
     * The function gets an agent and a graph and returns the nearest pokemon with the greatest value,
     * by compute the value/the distance.
     *
     * @param agent            the agent
     * @param ga               the graph
     * @param targetedPokemons the targeted pokemon
     * @param pokemonsList     the pokemonList
     * @return the nearest pokemon with the greatest value
     */
    private static CL_Pokemon getNearestPokemon(CL_Agent agent, dw_graph_algorithms ga, List<CL_Pokemon> targetedPokemons, List<CL_Pokemon> pokemonsList) {
        int srcNode = agent.getSrcNode();
        CL_Pokemon result = null;
        double distance, maxScore = 0;

        /*
        Iterates all the pokemons in the game that is not targeted yet,
        And checks which pokemon has the greatest valueForDistance.
         */
        for (CL_Pokemon currentPokemon : pokemonsList) {

            //Checks if the current pokemon is not targeted already.
            if (!targetedPokemons.contains(currentPokemon)) {
                int pokemonDest = getPokemonDest(agent, currentPokemon, ga.getGraph());
                distance = ga.shortestPathDist(srcNode, pokemonDest);
                if (distance > -1) {
                    double score = getValueForDistance(distance, currentPokemon);
                    if (score > maxScore) {
                        maxScore = score;
                        result = currentPokemon;
                    }
                }
            }
        }

        //Marks the pokemon as targeted (if found one) by adding it to the targeted list.
        if (result != null) {
            targetedPokemons.add(result);

        }

        //Returns the targeted pokemon.
        return result;
    }

    /**
     * The functions gets a distance and a pokemon and returns the quotient of the distance/the speed
     * of the pokemon.
     *
     * @param distance       the distance
     * @param currentPokemon the pokemon
     * @return the quotient of the distance/the speed of the pokemon
     */
    private static double getValueForDistance(double distance, CL_Pokemon currentPokemon) {
        return currentPokemon.getValue() / distance;
    }

    /**
     * The function gets a pokemon and a graph and returns the nearest src node to the pokemon
     *
     * @param currentPokemon the pokemon
     * @param graph          the graph
     * @return the nearest node to the pokemon
     */
    private static int getPokemonSrc(CL_Pokemon currentPokemon, directed_weighted_graph graph) {
        /*
            Checks the direction of the edge by its type:
            If the type is positive then the pokemon goes from the lesser to the greater node,
            so takes the minimum between src and dest.
            Else the pokemon goes from the greater to the lesser node,
            so takes the maximum between src and dest.
             */
        Arena.updateEdge(currentPokemon, graph);
        edge_data pokemonEdge = currentPokemon.get_edge();
        int pokemonSrc;
        if (currentPokemon.getType() > 0) {
            pokemonSrc = Math.min(pokemonEdge.getSrc(), pokemonEdge.getDest());
        } else {
            pokemonSrc = Math.max(pokemonEdge.getSrc(), pokemonEdge.getDest());
        }
        return pokemonSrc;
    }

    /**
     * The function gets a pokemon and a graph and returns the nearest dest node to the pokemon
     *
     * @param agent          the agent
     * @param currentPokemon the pokemon
     * @param graph          the graph
     * @return the nearest node to the pokemon
     */
    private static int getPokemonDest(CL_Agent agent, CL_Pokemon currentPokemon, directed_weighted_graph graph) {
        /*
        Checks the direction of the edge by its type:
        If the type is positive then the pokemon goes from the lesser to the greater node,
        so takes the minimum between src and dest.
            Else the pokemon goes from the greater to the lesser node,
            so takes the maximum between src and dest.

            Then checks if the Agent has to go to a greater node,
            if yes, then the pokemonDest will be the greatest node in the edge of the pokemon.
            Otherwise, the pokemonDest will be the lesser node in the edge of the pokemon.
             */

        Arena.updateEdge(currentPokemon, graph);
        edge_data pokemonEdge = currentPokemon.get_edge();
        int destArr[] = new int[2];
        int pokemonDest, alternativeDest, result;

        pokemonDest = Math.min(pokemonEdge.getSrc(), pokemonEdge.getDest());
        alternativeDest = Math.max(pokemonEdge.getSrc(), pokemonEdge.getDest());
        destArr[0] = pokemonDest;
        destArr[1] = alternativeDest;

        if (destArr[0] < agent.getSrcNode()) {
            result = destArr[0];
        } else {
            result = destArr[1];
        }

        return result;
    }

    /**
     * The function gets a pokemon and a graph and returns the next step towards that pokemon (the new dest).
     *
     * @param agent the agent
     * @param dest  the nearest node to the target pokemon
     * @param ga    the graph
     * @return the new destination of agent
     */
    private static int nextNode(CL_Agent agent, int dest, dw_graph_algorithms ga) {
        int src = agent.getSrcNode();
//        System.out.println("src = " + src);
//        System.out.println("from " + src + " to " + dest + ": " + ga.shortestPath(src, dest).toString());
//        System.out.println("next dest = " + ga.shortestPath(src, dest).get(1).getKey());
        return ga.shortestPath(src, dest).get(1).getKey();
    }
}
