import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.time.Duration;
import java.time.Instant;

public class SSOCounterExample {
    public static void main(String[] args) throws IOException {

        String inputFile = args[0];
        String inputLine = args[1];
        String[] inputs = inputLine.isEmpty() ? new String[0] : inputLine.split(":");

        Instant startTime = Instant.now();

        EdgeWeightedDigraph graph = new EdgeWeightedDigraph(inputFile, inputs);
        int E = 1;
        int V = graph.getV();

        Map<Integer, Integer> c = new ConcurrentHashMap<>();
        c.put(0, V);

        List<Integer> U = IntStream.range(0, V).boxed().collect(Collectors.toList());
        int[] ID = new int[V];
        int[][][] sig = new int[V][][];
        IntStream.range(0, V).parallel().forEach(i -> {
            sig[i] = new int[graph.getDegree(i)][2];
            ID[i] = 0;
        });

        int count = 0;
        int countU = V;

        List<Integer> nextU = new ArrayList<>(V);
        boolean[] inNextU = new boolean[V];

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);

        Map<Signature, Integer> signatureToPartitionMap = new ConcurrentHashMap<>();

        Instant startDealTime = Instant.now();

        while (countU != 0) {
            count++;
            nextU.clear();
            
            Arrays.fill(inNextU, false);

            List<Integer> finalU = U;
            finalU.parallelStream().forEach(x -> {
                TreeSet<int[]> tempSigSet = new TreeSet<>(Comparator.comparingInt((int[] a) -> a[0])
                                                                      .thenComparingInt(a -> a[1]));
                for (Pair<Integer, Integer> e : graph.adj(x)) {
                    tempSigSet.add(new int[]{e.getKey(), ID[e.getValue()]});
                }
                sig[x] = tempSigSet.toArray(new int[tempSigSet.size()][]);
            });

            Set<Integer> reusable = new HashSet<>();
            int[] countPerPartition = new int[E];

            for (int i = 0; i < countU; i++) {
                countPerPartition[ID[U.get(i)]]++;
            }

            for (int i = 0; i < E; i++) {
                if (c.get(i) != null && c.get(i) == countPerPartition[i]) {
                    reusable.add(i);
                }
            }

            for (int i = 0; i < countU; i++) {
                int x = U.get(i);
                int oid = ID[x];
                int[][] currentSig = sig[x];

                Signature currentSignature = new Signature(currentSig);
                Integer match = signatureToPartitionMap.get(currentSignature);

                if (match != null) {
                    ID[x] = match;
                } else if (!reusable.contains(ID[x])) {
                    c.put(E, 0);
                    ID[x] = E;
                    E++;
                } else {
                    reusable.remove(ID[x]);
                }

                signatureToPartitionMap.put(currentSignature, ID[x]);

                if (oid != ID[x]) {
                    for (int from : graph.getFromVertex(x)) {
                        if (!inNextU[from]) {
                            nextU.add(from);
                            inNextU[from] = true;
                        }
                    }
                    c.put(oid, c.get(oid) - 1);
                    c.put(ID[x], c.get(ID[x]) + 1);
                }
            }

            U = new ArrayList<>(nextU);
            countU = nextU.size();

        }

        Instant endDealTime = Instant.now();
        Duration durationDeal = Duration.between(startDealTime, endDealTime);

        int lineCount = countUnique(ID);

        int x = Integer.parseInt(args[2]);
        int y = Integer.parseInt(args[3]);

        strongVBisimulationAlgorithm(graph, x, y, ID);
    }

    private static int countUnique(int[] array) {
        return (int) Arrays.stream(array).distinct().count();
    }

    private static boolean areActionsEqual(EdgeWeightedDigraph graph, int x, int y) {
        int V = graph.getV();

        if (x < 0 || x >= V || y < 0 || y >= V) {
            throw new IllegalArgumentException("Vertex out of bounds");
        }

        Set<Integer> actionsX = new HashSet<>();
        Set<Integer> actionsY = new HashSet<>();

        for (Pair<Integer, Integer> edge : graph.adj(x)) {
            actionsX.add(edge.getKey());
        }

        for (Pair<Integer, Integer> edge : graph.adj(y)) {
            actionsY.add(edge.getKey()); 
        }

        return actionsX.equals(actionsY);
    }

private static void printDiffActions(EdgeWeightedDigraph graph, int x, int y) {
    Set<Integer> actionsX = new HashSet<>();
    Set<Integer> actionsY = new HashSet<>();

    for (Pair<Integer, Integer> edge : graph.adj(x)) {
        actionsX.add(edge.getKey());
    }
    for (Pair<Integer, Integer> edge : graph.adj(y)) {
        actionsY.add(edge.getKey()); 
    }

    Set<Integer> diffX = new HashSet<>(actionsX);
    diffX.removeAll(actionsY);

    Set<Integer> diffY = new HashSet<>(actionsY);
    diffY.removeAll(actionsX);

    if (!diffX.isEmpty()) {
        System.out.println(x + " can perform the following actions that " + y + " cannot:");
        for (Integer actionId : diffX) {
            String actionLabel = graph.getWeightLabel(actionId);
            System.out.println(actionLabel);
        }
    } else {
        System.out.println("compare with "+y+", "+x + " cannot perform any actions.");
    }

    if (!diffY.isEmpty()) {
        System.out.println(y + " can perform the following actions that " + x + " cannot:");
        for (Integer actionId : diffY) {
            String actionLabel = graph.getWeightLabel(actionId);
            System.out.println(actionLabel); 
        }
    } else {
        System.out.println("compare with "+x+", "+y + " cannot perform any actions.");
    }
}
private static void strongVBisimulationAlgorithm(EdgeWeightedDigraph graph, int x, int y, int[] ID) {

    if (ID[x] == ID[y]) {
        System.out.println("Strong V-Bisimulation holds for states " + x + " and " + y);
        return;
    }

    Queue<Pair<Integer, Integer>> queue = new LinkedList<>();
    Set<Pair<Integer, Integer>> visited = new HashSet<>();

    Map<Pair<Integer, Integer>, List<Transition>> xActionPaths = new HashMap<>();
    Map<Pair<Integer, Integer>, List<Transition>> yActionPaths = new HashMap<>();

    Pair<Integer, Integer> startState = new Pair<>(x, y);
    queue.add(startState);
    visited.add(startState);
    xActionPaths.put(startState, new ArrayList<>());
    yActionPaths.put(startState, new ArrayList<>());

    while (!queue.isEmpty()) {
        Pair<Integer, Integer> currentState = queue.poll();
        int currentX = currentState.getKey();
        int currentY = currentState.getValue();

        if (!areActionsEqual(graph, currentX, currentY)) {

            System.out.println("Counterexample found for states " + currentX + " and " + currentY);
            
            List<Transition> xCurrentPath = xActionPaths.get(currentState);
            System.out.println("X state transitions:");
            for (Transition transition : xCurrentPath) {
                System.out.println(transition);
            }

            List<Transition> yCurrentPath = yActionPaths.get(currentState);
            System.out.println("Y state transitions:");
            for (Transition transition : yCurrentPath) {
                System.out.println(transition); 
            }

            printDiffActions(graph, currentX, currentY);

            return;
        }

        Set<Integer> actionsX = new HashSet<>();
        Set<Integer> actionsY = new HashSet<>();

        for (Pair<Integer, Integer> edge : graph.adj(currentX)) {
            actionsX.add(edge.getKey());
        }

        for (Pair<Integer, Integer> edge : graph.adj(currentY)) {
            actionsY.add(edge.getKey()); 
        }

        Set<Integer> commonActions = new HashSet<>(actionsX);
        commonActions.retainAll(actionsY);

        for (Integer action : commonActions) {
            List<Pair<Integer, Integer>> nextStatesX = new ArrayList<>();
            List<Pair<Integer, Integer>> nextStatesY = new ArrayList<>();

            for (Pair<Integer, Integer> edgeX : graph.adj(currentX)) {
                if (edgeX.getKey().equals(action)) {
                    nextStatesX.add(new Pair<>(edgeX.getValue(), action));
                }
            }

            for (Pair<Integer, Integer> edgeY : graph.adj(currentY)) {
                if (edgeY.getKey().equals(action)) {
                    nextStatesY.add(new Pair<>(edgeY.getValue(), action));
                }
            }

            for (Pair<Integer, Integer> nextX : nextStatesX) {
                for (Pair<Integer, Integer> nextY : nextStatesY) {
                    Pair<Integer, Integer> nextState = new Pair<>(nextX.getKey(), nextY.getKey());

                    if (!visited.contains(nextState)) {

                        queue.add(nextState);
                        visited.add(nextState);

                        List<Transition> xCurrentPath = new ArrayList<>(xActionPaths.get(currentState));
                        String actionLabel = graph.getWeightLabel(action);
                        xCurrentPath.add(new Transition(currentX, actionLabel, nextX.getKey()));
                        xActionPaths.put(nextState, xCurrentPath);

                        List<Transition> yCurrentPath = new ArrayList<>(yActionPaths.get(currentState));
                        yCurrentPath.add(new Transition(currentY, actionLabel, nextY.getKey()));
                        yActionPaths.put(nextState, yCurrentPath);
                    }
                }
            }
        }

    }
}

}

class Signature {
    private final int[][] signature;

    public Signature(int[][] signature) {
        this.signature = signature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Signature that = (Signature) o;
        return Arrays.deepEquals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(signature);
    }
}

class Transition {
    private final int fromState;
    private final String action;
    private final int toState;

    public Transition(int fromState, String action, int toState) {
        this.fromState = fromState;
        this.action = action;
        this.toState = toState;
    }

    public int getFromState() {
        return fromState;
    }

    public String getAction() {
        return action;
    }

    public int getToState() {
        return toState;
    }

    @Override
    public String toString() {
        return "(" + fromState + ", " + action + ", " + toState + ")";
    }
}

