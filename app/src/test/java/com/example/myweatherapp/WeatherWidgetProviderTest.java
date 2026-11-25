package com.example.myweatherapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherWidgetProviderTest {

    @Test public void selectsVerticalLayoutForOneByTwo() {
        assertEquals(1, WeatherWidgetProvider.modeForDimensions(80, 180));
    }

    @Test public void selectsHorizontalLayoutForTwoByOne() {
        assertEquals(2, WeatherWidgetProvider.modeForDimensions(180, 80));
    }

    @Test public void selectsSquareLayoutForTwoByTwo() {
        assertEquals(6, WeatherWidgetProvider.modeForDimensions(180, 180));
    }

    @Test public void selectsTallLayoutForTwoByThree() {
        assertEquals(7, WeatherWidgetProvider.modeForDimensions(180, 260));
    }
}
