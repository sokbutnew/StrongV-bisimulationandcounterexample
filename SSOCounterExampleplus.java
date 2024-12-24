import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.time.Duration;
import java.time.Instant;

public class SSOCounterExampleplus {
    public static void main(String[] args) throws IOException {

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
        System.out.println("Number of blocks: " + lineCount);
        System.out.println("Number of iterations: " + count);
        System.out.println("Initialization time: " + duration.toMillis() + " ms");
        System.out.println("Processing time: " + durationDeal.toMillis() + " ms");

        System.out.println("\nStarting Strong V-Bisimulation Algorithm for states " + x + " and " + y + "...\n");

        strongVBisimulationAlgorithm(graph, x, y, ID);
    }

    private static int countUnique(int[] array) {
        return (int) Arrays.stream(array).distinct().count();
    }

private static void strongVBisimulationAlgorithm(EdgeWeightedDigraph graph, int x, int y, int[] ID) {
    List<String> labelsx = new ArrayList<>();
    List<String> labelsy = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Set<String> visiteds = new HashSet<>();

    while (x != -1 && y != -1) {
        if (ID[x] != ID[y]) {
            for (Pair<Integer, Integer> eX : graph.adj(x)) {
                int a = -1;
                int b = -1;
                int c = -1;
                boolean findMach = false;
                boolean findLabel = false;

                String stateKey = x + "," + eX.getKey() + "," + eX.getValue() + "," + y;
  
                if (visited.contains(stateKey)) {

                    if(visiteds.contains(stateKey)){
                        visited.remove(stateKey);
                    }
                    visiteds.add(stateKey);
                    continue;  
                }
           
                visited.add(stateKey);

                for (Pair<Integer, Integer> eY : graph.adj(y)) {
                    if (eX.getKey().equals(eY.getKey()) && ID[eX.getValue()] == ID[eY.getValue()]) {
                        findMach = true;
                        break;
                    } else if (eX.getKey().equals(eY.getKey()) && ID[eX.getValue()] != ID[eY.getValue()]) {

                        String stateKeyl = eX.getValue() + "," + eX.getKey() + "," + eY.getValue();
                        if (!visited.contains(stateKeyl)) {
                            a = eX.getValue();
                            b = eY.getValue();
                            c = eY.getKey();
                            findLabel = true;
                        }
                    }
                }

                if (!findMach && !findLabel) {
                    String weightLabelx = graph.getWeightLabel(eX.getKey());
                    System.out.println("Mismatch found: " + x + ", " + eX.getKey() + ", " + eX.getValue());
                    labelsx.add("Mismatch: " + x + ", " + weightLabelx + ", " + eX.getValue());
                    if (labelsx.isEmpty()) {
                        System.out.println(x + " can perform no action, while " + y + " can.");
                        return;
                    }
                    System.out.println("Counterexample found for states " + x + " and " + y);
                    labelsx.forEach(System.out::println);
                    labelsy.forEach(System.out::println);
                    return;
                } else if (!findMach && findLabel) {
                    String weightLabelx = graph.getWeightLabel(eX.getKey());
                    String weightLabely = graph.getWeightLabel(c);

                    labelsx.add(x + ", " + weightLabelx + ", " + eX.getValue());
                    labelsy.add(y + ", " + weightLabely + ", " + b);
                    x = a;
                    y = b;
                    break;
                }
            }
        } else {
            System.out.println("Strong V-Bisimulation holds for states " + x + " and " + y);
            return;
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
