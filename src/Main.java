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

/*
 * This program compares two files, like "git diff".
 *
 * You run it in one of two ways:
 *   Main lines     A B   -> show which lines were added or removed
 *   Main highlight A B   -> same, and also mark which letters changed in a line
 *
 * Reading the file from the bottom (main) upwards is the easiest way to follow
 * it: main reads the two files, runs the diff once, and then prints the result.
 */
public class Main {

    // One step of the answer. "tag" tells you what kind of step it is:
    //   '=' the line is the same in both files (keep it)
    //   '-' the line is only in file A (it was deleted)
    //   '+' the line is only in file B (it was added)
    // i = which line in A, j = which line in B.
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

    // Open the file and break it into a list of lines.
    // We read it as raw bytes (not text) so nothing gets changed along the way.
    // We cut a new line every time we see a newline byte. A newline at the very
    // end of the file does not create an extra blank line. A '\r' is left inside
    // the line as a normal character.
    static List<byte[]> readLines(String path) throws IOException {
        byte[] data = Files.readAllBytes(Path.of(path));
        List<byte[]> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == (byte) '\n') {
                lines.add(Arrays.copyOfRange(data, start, i));  // bytes from start up to the newline
                start = i + 1;                                  // next line starts after it
            }
        }
        // If there are leftover bytes after the last newline, that is the last line.
        if (start < data.length) {
            lines.add(Arrays.copyOfRange(data, start, data.length));
        }
        return lines;
    }

    // Give every different line its own number (its "id").
    // Why: comparing two numbers is much faster than comparing two whole lines,
    // and the diff compares lines thousands of times. We remember the number we
    // gave each line in a map, so the same line always gets the same number.
    static int[] internLines(List<byte[]> lines, Map<String, Integer> idOfLine) {
        int[] ids = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            String key = new String(lines.get(i), StandardCharsets.ISO_8859_1);  // the line as a text key
            Integer id = idOfLine.get(key);
            if (id == null) {            // first time we see this line
                id = idOfLine.size();    // give it the next free number
                idOfLine.put(key, id);
            }
            ids[i] = id;
        }
        return ids;
    }

    // This is the start of the diff. It just kicks off the real work (diff)
    // on the whole of both files and returns the list of steps.
    static List<Op> myersDiff(int[] a, int[] b) {
        List<Op> ops = new ArrayList<>();
        diff(a, 0, a.length, b, 0, b.length, ops);
        return ops;
    }

    // Compare the slice a[aStart..aEnd) with b[bStart..bEnd) and add the steps.
    // The plan: throw away the lines that already match at the front and the
    // back, then deal with whatever is left in the middle.
    static void diff(int[] a, int aStart, int aEnd, int[] b, int bStart, int bEnd, List<Op> ops) {
        // Front: while the first lines of both sides are equal, keep them.
        while (aStart < aEnd && bStart < bEnd && a[aStart] == b[bStart]) {
            ops.add(new Op('=', aStart, bStart));
            aStart++;
            bStart++;
        }

        // Back: while the last lines of both sides are equal, they are keeps too,
        // but they must be printed AFTER the middle. So just count them for now
        // and add them at the very end of this method.
        int commonSuffix = 0;
        while (aStart < aEnd && bStart < bEnd && a[aEnd - 1] == b[bEnd - 1]) {
            aEnd--;
            bEnd--;
            commonSuffix++;
        }

        int n = aEnd - aStart;   // how many lines are left in A
        int m = bEnd - bStart;   // how many lines are left in B
        if (n == 0) {
            // A side is empty, so every line left in B was added.
            for (int j = bStart; j < bEnd; j++) {
                ops.add(new Op('+', aStart, j));
            }
        } else if (m == 0) {
            // B side is empty, so every line left in A was deleted.
            for (int i = aStart; i < aEnd; i++) {
                ops.add(new Op('-', i, bStart));
            }
        } else {
            // Both sides still have lines. Find the "middle snake": a run of
            // matching lines that sits in the middle of the best answer. It
            // splits the work into two smaller pieces, which we solve the same
            // way. The snake itself is just keeps.
            int[] snake = findMiddleSnake(a, aStart, aEnd, b, bStart, bEnd);
            int x1 = snake[0];   // where the snake starts in A (counting from aStart)
            int y1 = snake[1];   // where the snake starts in B (counting from bStart)
            int x2 = snake[2];   // where the snake ends in A
            int y2 = snake[3];   // where the snake ends in B
            diff(a, aStart, aStart + x1, b, bStart, bStart + y1, ops);   // the part before the snake
            for (int t = 0; t < x2 - x1; t++) {                          // the snake (matching lines)
                ops.add(new Op('=', aStart + x1 + t, bStart + y1 + t));
            }
            diff(a, aStart + x2, aEnd, b, bStart + y2, bEnd, ops);       // the part after the snake
        }

        // Finally add the matching lines we trimmed off the back earlier.
        for (int s = 0; s < commonSuffix; s++) {
            ops.add(new Op('=', aEnd + s, bEnd + s));
        }
    }

    // Finds the middle snake, the key trick that keeps memory small.
    //
    // Think of a grid: file A along the top, file B down the side. You want the
    // cheapest path from the top-left corner to the bottom-right corner, where
    // going right = delete, going down = insert, and going diagonally is free
    // (it means the two lines match). This method searches for that path from
    // BOTH ends at once: one search creeps in from the top-left, the other from
    // the bottom-right. The moment the two searches touch, the matching run at
    // that meeting point is the "middle snake", and it neatly cuts the grid into
    // a top-left half and a bottom-right half.
    //
    // The only memory it uses is two small arrays, "forward" and "backward",
    // which store how far each search has reached. That is what makes it O(N).
    //
    // It returns {x1, y1, x2, y2}: the snake runs from (x1, y1) to (x2, y2).
    static int[] findMiddleSnake(int[] a, int aStart, int aEnd, int[] b, int bStart, int bEnd) {
        int n = aEnd - aStart;                 // lines in this A slice
        int m = bEnd - bStart;                 // lines in this B slice
        int delta = n - m;
        boolean oddDelta = (delta & 1) != 0;   // is (n - m) odd?
        int half = (n + m + 1) / 2;            // the searches never need to go further than this
        int offset = half + 1;                 // a shift so we can use negative positions in the arrays
        int size = 2 * (half + 1) + 1;
        int[] forward = new int[size];         // how far the forward search reached, per diagonal
        int[] backward = new int[size];        // how far the backward search reached, per diagonal
        forward[offset + 1] = 0;
        backward[offset + 1] = 0;

        // d is how many deletes/inserts we have spent so far. We try d = 0, 1, 2…
        // and stop as soon as the two searches meet, which gives the fewest edits.
        for (int d = 0; d <= half; d++) {

            // ---- forward search: one layer deeper ----
            for (int k = -d; k <= d; k += 2) {     // k picks which diagonal we are on
                int index = offset + k;
                int x;
                // Step onto this diagonal from whichever neighbour reached further:
                // from above means an insert, from the left means a delete.
                if (k == -d || (k != d && forward[index - 1] < forward[index + 1])) {
                    x = forward[index + 1];
                } else {
                    x = forward[index - 1] + 1;
                }
                int y = x - k;
                int xStart = x;                     // remember where the snake begins
                int yStart = y;
                // Slide along the diagonal for free while the lines keep matching.
                while (x < n && y < m && a[aStart + x] == b[bStart + y]) {
                    x++;
                    y++;
                }
                forward[index] = x;                 // record how far we got
                // Did we land on a diagonal the backward search already reached?
                // If so, the two searches overlap here and we found the middle snake.
                if (oddDelta && k >= delta - (d - 1) && k <= delta + (d - 1)) {
                    int mirror = offset + (delta - k);
                    if (x + backward[mirror] >= n) {
                        return new int[]{xStart, yStart, x, y};
                    }
                }
            }

            // ---- backward search: one layer deeper, from the opposite corner ----
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
                // Same sliding, but it compares lines counted from the END of each side.
                while (x < n && y < m && a[aStart + n - 1 - x] == b[bStart + m - 1 - y]) {
                    x++;
                    y++;
                }
                backward[index] = x;
                if (!oddDelta && (delta - k) >= -d && (delta - k) <= d) {
                    int mirror = offset + (delta - k);
                    if (forward[mirror] + x >= n) {
                        // This snake is measured from the end, so convert it back
                        // to normal (top-left) numbers before returning it.
                        return new int[]{n - x, m - y, n - xStart, m - yStart};
                    }
                }
            }
        }
        return new int[]{0, 0, 0, 0};   // never happens for real input
    }

    // Break a line of text into its characters. We use "code points" so that an
    // emoji counts as one character, even though Java stores it as two.
    static int[] toCodePoints(String text) {
        int count = text.codePointCount(0, text.length());
        int[] points = new int[count];
        int out = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            points[out] = cp;
            out++;
            i += Character.charCount(cp);   // most characters are 1, emoji are 2
        }
        return points;
    }

    // Turn a list of changed positions into text like "3-5,9-12".
    // Numbers that run on one after another are joined into one range, the end
    // number is not counted in, and if nothing changed we write just ".".
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
                prev = pos;                 // still part of the same run
            } else {
                if (result.length() > 0) {
                    result.append(',');
                }
                result.append(start).append('-').append(prev + 1);
                start = pos;                // begin a new run
                prev = pos;
            }
        }
        if (result.length() > 0) {
            result.append(',');
        }
        result.append(start).append('-').append(prev + 1);
        return result.toString();
    }

    // For one changed line pair, work out which characters differ. We do this by
    // running the SAME diff, but on the characters of the two lines instead of
    // on the lines of the two files. Then we collect the deleted positions (old)
    // and the added positions (new) and build the "? old | new" line.
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

    // Write one output line: the prefix character, the line's bytes, then a newline.
    static void writeLine(OutputStream out, char prefix, byte[] line) throws IOException {
        out.write((byte) prefix);
        out.write(line);
        out.write('\n');
    }

    // Print the Part A answer. Matching lines get a space in front. For a block
    // of changes, the assignment wants all deleted lines first, then all added
    // lines, so we gather them and print them in that order.
    static void formatLines(List<Op> ops, List<byte[]> aLines, List<byte[]> bLines,
                            OutputStream out) throws IOException {
        int i = 0;
        while (i < ops.size()) {
            if (ops.get(i).tag == '=') {
                writeLine(out, ' ', aLines.get(ops.get(i).i));   // a kept line
                i++;
            } else {
                List<Integer> deletes = new ArrayList<>();
                List<Integer> inserts = new ArrayList<>();
                while (i < ops.size() && ops.get(i).tag != '=') {   // gather one block of changes
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

    // Print the Part B answer: everything Part A prints, plus, after each added
    // line that has a matching deleted line, a "?" line showing the changed
    // characters. The 1st delete pairs with the 1st insert, 2nd with 2nd, etc.
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
                    if (idx < deletes.size()) {    // this added line has a matching deleted line
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
        // We need exactly three things: the command, file A, and file B.
        if (args.length != 3) {
            System.err.println("usage: Main lines|highlight A B");
            System.exit(2);
        }
        String command = args[0];
        String pathA = args[1];
        String pathB = args[2];

        // Read both files first. If either one cannot be read, we print nothing
        // normal, show an error, and stop with exit code 2 (as the rules ask).
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

        // Turn the lines into id numbers, then run the diff once.
        Map<String, Integer> idOfLine = new HashMap<>();
        int[] aIds = internLines(aLines, idOfLine);
        int[] bIds = internLines(bLines, idOfLine);
        List<Op> ops = myersDiff(aIds, bIds);

        // Sending lots of tiny writes straight to the screen is slow, so we wrap
        // the output in a buffer and push it all out once at the end.
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