package com.example.client.utils;

public class TextUtils {
    public static String leftOf(String text, String target) {
        if (text == null || target == null || target.isEmpty()) {
            return "";
        }

        int index = text.indexOf(target);

        if (index == -1) {
            return "";
        }

        return text.substring(0, index).trim();
    }

    public static String rightOf(String text, String target) {
        if (text == null || target == null || target.isEmpty()) {
            return "";
        }

        int index = text.indexOf(target);

        if (index == -1) {
            return "";
        }

        return text.substring(index + target.length()).trim();
    }

    public static String between(String text, String left, String right) {
        if (text == null || left == null || right == null) {
            return "";
        }

        if (left.isEmpty() || right.isEmpty()) {
            return "";
        }

        int leftIndex = text.indexOf(left);

        if (leftIndex == -1) {
            return "";
        }

        int start = leftIndex + left.length();

        int rightIndex = text.indexOf(right, start);

        if (rightIndex == -1) {
            return "";
        }

        return text.substring(start, rightIndex).trim();
    }
}
