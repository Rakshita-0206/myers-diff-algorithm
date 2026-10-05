import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    static class Op {
        char tag;
        int i;
        int j;

        Op(char tag, int i, int j) {
            this.tag = tag;
            this.i = i;
            this.j = j;
        }
    }

    static List<byte[]> readLines(String path) throws IOException {
        byte[] data = Files.readAllBytes(Path.of(path));
        List<byte[]> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == (byte) '\n') {
                lines.add(Arrays.copyOfRange(data, start, i));
                start = i + 1;
            }
        }
        if (start < data.length) {
            lines.add(Arrays.copyOfRange(data, start, data.length));
        }
        return lines;
    }

    static int[] internLines(List<byte[]> lines, Map<String, Integer> idOfLine) {
        int[] ids = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            String key = new String(lines.get(i), StandardCharsets.ISO_8859_1);
            Integer id = idOfLine.get(key);
            if (id == null) {
                id = idOfLine.size();
                idOfLine.put(key, id);
            }
            ids[i] = id;
        }
        return ids;
    }

    static List<Op> myersDiff(int[] a, int[] b) {
        List<Op> ops = new ArrayList<>();
        diff(a, 0, a.length, b, 0, b.length, ops);
        return ops;
    }

    static void diff(int[] a, int aStart, int aEnd, int[] b, int bStart, int bEnd, List<Op> ops) {
        while (aStart < aEnd && bStart < bEnd && a[aStart] == b[bStart]) {
            ops.add(new Op('=', aStart, bStart));
            aStart++;
            bStart++;
        }

        int commonSuffix = 0;
        while (aStart < aEnd && bStart < bEnd && a[aEnd - 1] == b[bEnd - 1]) {
            aEnd--;
            bEnd--;
            commonSuffix++;
        }

        int n = aEnd - aStart;
        int m = bEnd - bStart;
        if (n == 0) {
            for (int j = bStart; j < bEnd; j++) {
                ops.add(new Op('+', aStart, j));
            }
        } else if (m == 0) {
            for (int i = aStart; i < aEnd; i++) {
                ops.add(new Op('-', i, bStart));
            }
        } else {
            int[] snake = findMiddleSnake(a, aStart, aEnd, b, bStart, bEnd);
            int x1 = snake[0];
            int y1 = snake[1];
            int x2 = snake[2];
            int y2 = snake[3];
            diff(a, aStart, aStart + x1, b, bStart, bStart + y1, ops);
            for (int t = 0; t < x2 - x1; t++) {
                ops.add(new Op('=', aStart + x1 + t, bStart + y1 + t));
            }
            diff(a, aStart + x2, aEnd, b, bStart + y2, bEnd, ops);
        }

        for (int s = 0; s < commonSuffix; s++) {
            ops.add(new Op('=', aEnd + s, bEnd + s));
        }
    }

    static int[] findMiddleSnake(int[] a, int aStart, int aEnd, int[] b, int bStart, int bEnd) {
        int n = aEnd - aStart;
        int m = bEnd - bStart;
        int delta = n - m;
        boolean oddDelta = (delta & 1) != 0;
        int half = (n + m + 1) / 2;
        int offset = half + 1;
        int size = 2 * (half + 1) + 1;
        int[] forward = new int[size];
        int[] backward = new int[size];
        forward[offset + 1] = 0;
        backward[offset + 1] = 0;

        for (int d = 0; d <= half; d++) {
            for (int k = -d; k <= d; k += 2) {
                int index = offset + k;
                int x;
                if (k == -d || (k != d && forward[index - 1] < forward[index + 1])) {
                    x = forward[index + 1];
                } else {
                    x = forward[index - 1] + 1;
                }
                int y = x - k;
                int xStart = x;
                int yStart = y;
                while (x < n && y < m && a[aStart + x] == b[bStart + y]) {
                    x++;
                    y++;
                }
                forward[index] = x;
                if (oddDelta && k >= delta - (d - 1) && k <= delta + (d - 1)) {
                    int mirror = offset + (delta - k);
                    if (x + backward[mirror] >= n) {
                        return new int[]{xStart, yStart, x, y};
                    }
                }
            }
            for (int k = -d; k <= d; k += 2) {
                int index = offset + k;
                int x;
                if (k == -d || (k != d && backward[index - 1] < backward[index + 1])) {
                    x = backward[index + 1];
                } else {
                    x = backward[index - 1] + 1;
                }
                int y = x - k;
                int xStart = x;
                int yStart = y;
                while (x < n && y < m && a[aStart + n - 1 - x] == b[bStart + m - 1 - y]) {
                    x++;
                    y++;
                }
                backward[index] = x;
                if (!oddDelta && (delta - k) >= -d && (delta - k) <= d) {
                    int mirror = offset + (delta - k);
                    if (forward[mirror] + x >= n) {
                        return new int[]{n - x, m - y, n - xStart, m - yStart};
                    }
                }
            }
        }
        return new int[]{0, 0, 0, 0};
    }

    static int[] toCodePoints(String text) {
        int count = text.codePointCount(0, text.length());
        int[] points = new int[count];
        int out = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            points[out] = cp;
            out++;
            i += Character.charCount(cp);
        }
        return points;
    }

    static String rangesToText(List<Integer> positions) {
        if (positions.isEmpty()) {
            return ".";
        }
        StringBuilder result = new StringBuilder();
        int start = positions.get(0);
        int prev = positions.get(0);
        for (int idx = 1; idx < positions.size(); idx++) {
            int pos = positions.get(idx);
            if (pos == prev + 1) {
                prev = pos;
            } else {
                if (result.length() > 0) {
                    result.append(',');
                }
                result.append(start).append('-').append(prev + 1);
                start = pos;
                prev = pos;
            }
        }
        if (result.length() > 0) {
            result.append(',');
        }
        result.append(start).append('-').append(prev + 1);
        return result.toString();
    }

    static byte[] highlightPair(byte[] oldBytes, byte[] newBytes) {
        int[] oldChars = toCodePoints(new String(oldBytes, StandardCharsets.UTF_8));
        int[] newChars = toCodePoints(new String(newBytes, StandardCharsets.UTF_8));
        List<Op> ops = myersDiff(oldChars, newChars);
        List<Integer> oldChanged = new ArrayList<>();
        List<Integer> newChanged = new ArrayList<>();
        for (Op op : ops) {
            if (op.tag == '-') {
                oldChanged.add(op.i);
            } else if (op.tag == '+') {
                newChanged.add(op.j);
            }
        }
        String text = "? " + rangesToText(oldChanged) + " | " + rangesToText(newChanged);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static void writeLine(OutputStream out, char prefix, byte[] line) throws IOException {
        out.write((byte) prefix);
        out.write(line);
        out.write('\n');
    }

    static void formatLines(List<Op> ops, List<byte[]> aLines, List<byte[]> bLines,
                            OutputStream out) throws IOException {
        int i = 0;
        while (i < ops.size()) {
            if (ops.get(i).tag == '=') {
                writeLine(out, ' ', aLines.get(ops.get(i).i));
                i++;
            } else {
                List<Integer> deletes = new ArrayList<>();
                List<Integer> inserts = new ArrayList<>();
                while (i < ops.size() && ops.get(i).tag != '=') {
                    if (ops.get(i).tag == '-') {
                        deletes.add(ops.get(i).i);
                    } else {
                        inserts.add(ops.get(i).j);
                    }
                    i++;
                }
                for (int ai : deletes) {
                    writeLine(out, '-', aLines.get(ai));
                }
                for (int bi : inserts) {
                    writeLine(out, '+', bLines.get(bi));
                }
            }
        }
    }

    static void formatHighlight(List<Op> ops, List<byte[]> aLines, List<byte[]> bLines,
                                OutputStream out) throws IOException {
        int i = 0;
        while (i < ops.size()) {
            if (ops.get(i).tag == '=') {
                writeLine(out, ' ', aLines.get(ops.get(i).i));
                i++;
            } else {
                List<Integer> deletes = new ArrayList<>();
                List<Integer> inserts = new ArrayList<>();
                while (i < ops.size() && ops.get(i).tag != '=') {
                    if (ops.get(i).tag == '-') {
                        deletes.add(ops.get(i).i);
                    } else {
                        inserts.add(ops.get(i).j);
                    }
                    i++;
                }
                for (int ai : deletes) {
                    writeLine(out, '-', aLines.get(ai));
                }
                for (int idx = 0; idx < inserts.size(); idx++) {
                    int bi = inserts.get(idx);
                    writeLine(out, '+', bLines.get(bi));
                    if (idx < deletes.size()) {
                        byte[] highlight = highlightPair(aLines.get(deletes.get(idx)),
                                                         bLines.get(bi));
                        out.write(highlight);
                        out.write('\n');
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("usage: Main lines|highlight A B");
            System.exit(2);
        }
        String command = args[0];
        if (!command.equals("lines") && !command.equals("highlight")) {
            System.err.println("unknown command: " + command);
            System.exit(2);
        }
        String pathA = args[1];
        String pathB = args[2];

        List<byte[]> aLines;
        List<byte[]> bLines;
        try {
            aLines = readLines(pathA);
            bLines = readLines(pathB);
        } catch (Exception e) {
            System.err.println("error reading input file: " + e.getMessage());
            System.exit(2);
            return;
        }

        Map<String, Integer> idOfLine = new HashMap<>();
        int[] aIds = internLines(aLines, idOfLine);
        int[] bIds = internLines(bLines, idOfLine);
        List<Op> ops = myersDiff(aIds, bIds);

        OutputStream out = new BufferedOutputStream(System.out);
        try {
            if (command.equals("lines")) {
                formatLines(ops, aLines, bLines, out);
            } else if (command.equals("highlight")) {
                formatHighlight(ops, aLines, bLines, out);
            } else {
                System.err.println("unknown command: " + command);
                System.exit(2);
            }
            out.flush();
        } catch (IOException e) {
            System.err.println("error writing output: " + e.getMessage());
            System.exit(2);
        }
    }
}