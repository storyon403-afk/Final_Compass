package cn.finalscompass.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ApiModelsTest {
    @Test
    void discussionInputContainsNoClientControlledIdentity() {
        var fields = Arrays.stream(ApiModels.CreateDiscussion.class.getRecordComponents())
                .map(component -> component.getName()).toList();

        assertThat(fields).containsExactly("content", "parentId");
    }
}
