import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.time.Duration;
import java.time.Instant;

public class SSOCounterExample {
    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream("output.txt");
        System.setOut(out);

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

            System.out.println(countU);
        }

        Instant endDealTime = Instant.now();
        Duration durationDeal = Duration.between(startDealTime, endDealTime);

        int lineCount = countUnique(ID);
        System.out.println("Number of partitions: " + lineCount);
        System.out.println("Number of iterations: " + count);
        System.out.println("Initialization time: " + duration.toMillis() + " ms");
        System.out.println("Processing time: " + durationDeal.toMillis() + " ms");
        boolean bisimilar = checkStrongVBisimulation(0, 3, sig, ID, graph);
        if (!bisimilar) {
            System.out.println("Vertices 0 and 3 are not bisimilar.");
        } else {
            System.out.println("Vertices 0 and 3 are " + Arrays.toString(inputs) + " bisimilar.");
        }
    }

    public static boolean checkStrongVBisimulation(int x, int y, int[][][] sig, int[] ID, EdgeWeightedDigraph graph) {

        if (ID[x] != ID[y]) {
            Set<Integer> labels = new HashSet<>();

            for (int[] labelIdX : sig[x]) {
                int marktoX = labelIdX[1];
                boolean found = false;
                
                for (int[] labelIdY : sig[y]) {
                    if (labelIdX[0] == labelIdY[0] && marktoX == labelIdY[1]) { 
                        found = true;
                        break; 
                    }
                }

                if (!found) {
                    labels.add(labelIdX[0]);
                }
            }

            if (!labels.isEmpty()) {

                StringBuilder failedLabels = new StringBuilder();
                for (int labelId : labels) {
                    String weightLabel = graph.getWeightLabel(labelId);
                    failedLabels.append(weightLabel).append(", ");
                }
                System.out.println("Bisimulation failed at vertex: " + x + " with labels: " + failedLabels.toString());
                return false;
            }
    
            labels.clear();
            for (int[] labelIdY : sig[y]) {
                int marktoY = labelIdY[1];
                boolean found = false;
                for (int[] labelIdX : sig[x]) {
                    if (labelIdY[0] == labelIdX[0] && marktoY == labelIdX[1]) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    labels.add(labelIdY[0]);
                }
            }
    
            if (!labels.isEmpty()) {
                StringBuilder failedLabels = new StringBuilder();
                for (int labelId : labels) {
                    String weightLabel = graph.getWeightLabel(labelId);
                    failedLabels.append(weightLabel).append(", ");
                }
                System.out.println("Bisimulation failed at vertex: " + y + " with labels: " + failedLabels.toString());
                return false;
            }
        }
    
        return true; 
    }

    private static int countUnique(int[] array) {
        return (int) Arrays.stream(array).distinct().count();
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
