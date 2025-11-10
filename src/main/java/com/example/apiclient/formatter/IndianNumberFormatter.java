package com.example.apiclient.formatter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class IndianNumberFormatter {
    public static String formatIndianNumberWithDecimals(double number) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("en", "IN"));
        symbols.setGroupingSeparator(',');

        DecimalFormat formatter = new DecimalFormat("#,##,###.00", symbols);
        return formatter.format(number);
    }
}
