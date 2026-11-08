package vn.edu.phenikaa.canteen.ocr;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rút họ tên, mã sinh viên, email từ chữ OCR thô. Không phụ thuộc Spring để kiểm thử độc lập.
 * <p>
 * OCR sai là chuyện thường, nên bộ rút chỉ đưa ra GỢI Ý theo thứ tự ưu tiên: dòng có nhãn ("Họ và tên", "MSV"...)
 * trước, rồi mới đến suy đoán theo hình dạng. Kết quả luôn do người dùng xác nhận (UC1.4), không bao giờ lưu thẳng.
 */
public final class StudentCardParser {

    private static final Pattern NAME_LABEL = Pattern.compile(
            "(?iu)^\\s*(?:họ\\s*(?:và|&|va)?\\s*tên|ho\\s*(?:va|&)?\\s*ten|full\\s*name|name)\\s*[:：.\\-]?\\s*(.*)$");
    private static final Pattern CODE_LABEL = Pattern.compile(
            "(?iu)(?:mã\\s*(?:số\\s*)?(?:sv|sinh\\s*viên)|ma\\s*(?:so\\s*)?(?:sv|sinh\\s*vien)|msv|student\\s*(?:id|code|no)|\\bid\\b)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern WORD = Pattern.compile("^[\\p{L}][\\p{L}'’.\\-]*$");
    /** Dòng tiêu đề của thẻ, không phải tên người. So sánh sau khi bỏ dấu và đưa về chữ thường. */
    private static final List<String> NOT_A_NAME = List.of(
            "dai hoc", "truong", "the sinh vien", "sinh vien", "student", "card", "university", "phenikaa",
            "khoa", "nganh", "khoa hoc", "lop", "class", "ngay sinh", "birth", "han su dung", "valid", "expire",
            "bo giao duc", "cong hoa", "viet nam", "ma so", "dia chi", "address");

    private static final int MAX_NAME_LEN = 120;

    private final Pattern codePattern;

    public StudentCardParser(String studentCodeRegex) {
        this.codePattern = Pattern.compile("(?<![\\p{L}\\d])(?:" + studentCodeRegex + ")(?![\\p{L}\\d])");
    }

    public ParsedCard parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ParsedCard(null, null, null);
        }
        List<String> lines = new ArrayList<>();
        for (String l : Normalizer.normalize(rawText, Normalizer.Form.NFC).split("\\R")) {
            String t = l.replaceAll("[\\t ]+", " ").trim();
            if (!t.isEmpty()) {
                lines.add(t);
            }
        }
        return new ParsedCard(findName(lines), findCode(lines), findEmail(lines));
    }

    // ------------------------------------------------------------------ mã sinh viên

    private String findCode(List<String> lines) {
        String firstAny = null;
        for (String line : lines) {
            Matcher m = codePattern.matcher(line);
            if (!m.find()) {
                continue;
            }
            if (CODE_LABEL.matcher(line).find()) {
                return m.group();                            // dòng có nhãn mã sinh viên: chắc nhất
            }
            if (firstAny == null) {
                firstAny = m.group();
            }
        }
        // Nhãn nằm một dòng, số nằm dòng kế tiếp.
        for (int i = 0; i + 1 < lines.size(); i++) {
            if (CODE_LABEL.matcher(lines.get(i)).find()) {
                Matcher m = codePattern.matcher(lines.get(i + 1));
                if (m.find()) {
                    return m.group();
                }
            }
        }
        return firstAny;
    }

    // ------------------------------------------------------------------ email

    private String findEmail(List<String> lines) {
        for (String line : lines) {
            Matcher m = EMAIL.matcher(line);
            if (m.find()) {
                return m.group().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ họ tên

    private String findName(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = NAME_LABEL.matcher(lines.get(i));
            if (!m.matches()) {
                continue;
            }
            String value = m.group(1).trim();
            if (value.isEmpty() && i + 1 < lines.size()) {
                value = lines.get(i + 1);                    // nhãn một dòng, tên dòng kế
            }
            String name = cleanName(value);
            if (name != null) {
                return name;
            }
        }
        // Không có nhãn: lấy dòng giống tên người nhất (2 đến 6 từ, toàn chữ, ưu tiên chữ HOA, không phải tiêu đề).
        String best = null;
        int bestScore = 0;
        for (String line : lines) {
            String name = cleanName(line);
            if (name == null || isHeading(name)) {
                continue;
            }
            int words = name.split(" ").length;
            if (words < 2 || words > 6) {
                continue;
            }
            int score = 10 + (isUpper(line) ? 5 : 0) + (words >= 3 ? 2 : 0);
            if (score > bestScore) {
                best = name;
                bestScore = score;
            }
        }
        return best;
    }

    /** Bỏ ký tự rác ở hai đầu, kiểm tra từng từ toàn chữ cái, và đổi chữ HOA toàn bộ thành dạng viết hoa chữ đầu. */
    private static String cleanName(String value) {
        String v = value.replaceAll("^[^\\p{L}]+|[^\\p{L}]+$", "").replaceAll("\\s+", " ");
        if (v.isEmpty() || v.length() > MAX_NAME_LEN) {
            return null;
        }
        for (String w : v.split(" ")) {
            if (!WORD.matcher(w).matches()) {
                return null;                                 // có chữ số hoặc ký hiệu: không phải tên
            }
        }
        return isUpper(v) ? titleCase(v) : v;
    }

    private static boolean isHeading(String name) {
        String flat = stripAccents(name).toLowerCase(Locale.ROOT);
        for (String bad : NOT_A_NAME) {
            if (flat.contains(bad)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUpper(String s) {
        boolean hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (Character.isLowerCase(c)) {
                    return false;
                }
            }
        }
        return hasLetter;
    }

    private static String titleCase(String s) {
        Locale vi = Locale.forLanguageTag("vi");
        StringBuilder out = new StringBuilder();
        for (String w : s.toLowerCase(vi).split(" ")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(w.substring(0, 1).toUpperCase(vi)).append(w.substring(1));
        }
        return out.toString();
    }

    private static String stripAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
    }
}
