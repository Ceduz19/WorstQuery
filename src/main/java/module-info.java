/**
 * SQL construction with public domain factories and an explicit extension SPI.
 * Internal packages are deliberately neither exported nor opened.
 */
module com.github.ceduz19.worstquery {
    exports com.github.ceduz19.worstquery.api;
    exports com.github.ceduz19.worstquery.ddl;
    exports com.github.ceduz19.worstquery.dialect;
    exports com.github.ceduz19.worstquery.dml;
    exports com.github.ceduz19.worstquery.expression;
    exports com.github.ceduz19.worstquery.fragment;
    exports com.github.ceduz19.worstquery.model;
    exports com.github.ceduz19.worstquery.spi;
}
