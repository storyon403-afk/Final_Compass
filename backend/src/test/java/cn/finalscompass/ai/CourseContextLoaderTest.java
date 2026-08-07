package cn.finalscompass.ai;

import cn.finalscompass.ai.context.CourseContextLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CourseContextLoaderTest {
    @Test void returnsEmptyContextWhenRequestHasNoCourse() {
        var context = new CourseContextLoader(null).load(null, null);
        assertFalse(context.available());
        assertTrue(context.materials().isEmpty());
    }
}
