import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EdgeWeightedDigraph {
    private int[][] fromVertex;
    private Pair<Integer, Integer>[][] edgeData;
    private final Map<String, Integer> weightMap = new ConcurrentHashMap<>();
    private final Map<Integer, String> inverseWeightMap = new ConcurrentHashMap<>();
    private int weightCounter;
    private int V;

    private static final Pattern EDGE_PATTERN = Pattern.compile("^\\((\\d+),\\s*\"?(.*?)\"?,\\s*(\\d+)\\)$");

    public EdgeWeightedDigraph(String inputFile, String[] strings) throws IOException {
        processFile(inputFile, strings);
    }

    @SuppressWarnings("unchecked")
    private void processFile(String inputFile, String[] strings) throws IOException {
        List<String> edgeLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("des (0,")) {
                    parseDesLine(line);
                } else {
                    edgeLines.add(line);
                }
            }
        }

        List<List<Integer>> tempFromVertex = new ArrayList<>(V);
        List<List<Pair<Integer, Integer>>> tempEdgeData = new ArrayList<>(V);

        for (int i = 0; i < V; i++) {
            tempFromVertex.add(new ArrayList<>());
            tempEdgeData.add(new ArrayList<>());
        }

        for (String edgeLine : edgeLines) {
            processAndStoreLine(edgeLine, strings, tempFromVertex, tempEdgeData);
        }

        fromVertex = new int[V][];
        edgeData = new Pair[V][];

        for (int i = 0; i < V; i++) {
            fromVertex[i] = tempFromVertex.get(i).stream().mapToInt(Integer::intValue).toArray();
            edgeData[i] = tempEdgeData.get(i).toArray(new Pair[0]);
        }

        tempFromVertex = null;
        tempEdgeData = null;
    }

    private void parseDesLine(String line) {
        String[] nums = line.substring(6, line.length() - 1).split(",");
        this.V = Integer.parseInt(nums[2].trim());
    }

    private void processAndStoreLine(String line, String[] strings, List<List<Integer>> tempFromVertex, List<List<Pair<Integer, Integer>>> tempEdgeData) {
        Matcher matcher = EDGE_PATTERN.matcher(line);
        if (matcher.matches()) {
            int v = Integer.parseInt(matcher.group(1).trim());
            String weight = matcher.group(2).trim();
            int w = Integer.parseInt(matcher.group(3).trim());

            if (strings.length == 0 || !Arrays.asList(strings).contains(weight)) {
                int weightId = weightMap.computeIfAbsent(weight, k -> weightCounter++); 
                inverseWeightMap.put(weightId, weight);
                tempEdgeData.get(v).add(new Pair<>(weightId, w));
                tempFromVertex.get(w).add(v);
            }
        }
    }

    public Pair<Integer, Integer>[][] getEdgeData() {
        return edgeData;
    }

    public int getV() {
        return V;
    }

    public Pair<Integer, Integer>[] adj(int v) {
        return edgeData[v];
    }

    public int[] getFromVertex(int v) {
        if (v < 0 || v >= V) {
            throw new IllegalArgumentException("Vertex " + v + " is out of bounds");
        }
        return fromVertex[v];
    }

    public int getDegree(int v) {
        if (v < 0 || v >= V) {
            throw new IllegalArgumentException("Vertex " + v + " is out of bounds");
        }
        return edgeData[v].length;
    }

    public String getWeightLabel(int weightId) {
        return inverseWeightMap.get(weightId);
    }
}
