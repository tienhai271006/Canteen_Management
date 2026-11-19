package vn.edu.phenikaa.canteen.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StudentCardParserTest {

    private final StudentCardParser parser = new StudentCardParser("\\d{8,10}");

    @Test
    void readsLabelledFieldsAndTitleCasesUppercaseName() {
        ParsedCard c = parser.parse("TRƯỜNG ĐẠI HỌC PHENIKAA\nTHẺ SINH VIÊN\nHọ và tên: NGUYỄN VĂN AN\n"
                + "Mã SV: 21010123\nKhoa: Công nghệ thông tin");
        assertThat(c.fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(c.studentCode()).isEqualTo("21010123");
        assertThat(c.email()).isNull();
    }

    @Test
    void guessesNameWithoutLabelAndSkipsHeadings() {
        ParsedCard c = parser.parse("PHENIKAA UNIVERSITY\nSTUDENT CARD\nTRẦN THỊ BÍCH NGỌC\nStudent ID 22011234\n"
                + "Ngocbich@Phenikaa-Uni.edu.vn");
        assertThat(c.fullName()).isEqualTo("Trần Thị Bích Ngọc");
        assertThat(c.studentCode()).isEqualTo("22011234");
        assertThat(c.email()).isEqualTo("ngocbich@phenikaa-uni.edu.vn");
    }

    @Test
    void labelOnOneLineValueOnTheNext() {
        ParsedCard c = parser.parse("Họ tên\nLê Hoàng Nam\nMSV\n20010999");
        assertThat(c.fullName()).isEqualTo("Lê Hoàng Nam");
        assertThat(c.studentCode()).isEqualTo("20010999");
    }

    @Test
    void labelledCodeBeatsOtherLongNumbers() {
        ParsedCard c = parser.parse("SĐT 0912345678\nMã sinh viên: 21010123");
        assertThat(c.studentCode()).isEqualTo("21010123");
    }

    @Test
    void nameWithDigitsOrSymbolsIsRejected() {
        assertThat(parser.parse("Họ và tên: Phạm 0ng").fullName()).isNull();
    }

    @Test
    void numbersOutsideConfiguredLengthAreNotCodes() {
        assertThat(parser.parse("Mã SV 123456789012").studentCode()).isNull();
    }

    @Test
    void garbageOrEmptyTextIsNotUsable() {
        assertThat(parser.parse("@@ ## !!\n12").usable()).isFalse();
        assertThat(parser.parse(null).isEmpty()).isTrue();
        assertThat(parser.parse("   ").isEmpty()).isTrue();
    }
}
