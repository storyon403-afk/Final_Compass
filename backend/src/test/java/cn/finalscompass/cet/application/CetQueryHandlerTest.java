package cn.finalscompass.cet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.finalscompass.cet.domain.CetQueryRepository;
import cn.finalscompass.controller.CetController.CetItem;
import cn.finalscompass.controller.CetController.CetPaper;
import cn.finalscompass.controller.CetController.SectionResource;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CetQueryHandlerTest {
  @Test
  void normalizesPublicQueryBeforeCallingRepository() {
    var repository = new StubRepository();
    var handler = new CetQueryHandler(repository, new AuthorizationPolicy());
    handler.items("cet4", "practice", "  WRITING  ");
    assertThat(repository.query).isEqualTo(List.of("CET4", "PRACTICE", "WRITING"));
  }

  @Test
  void rejectsUnsupportedLevelAtApplicationBoundary() {
    var handler = new CetQueryHandler(new StubRepository(), new AuthorizationPolicy());
    assertThatThrownBy(() -> handler.papers("TEM8"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("考试级别不正确");
  }

  private static final class StubRepository implements CetQueryRepository {
    private List<String> query;
    @Override public List<CetPaper> findPublishedPapers(String level) { return List.of(); }
    @Override public List<CetItem> findPublishedItems(String level,String mode,String section) {
      query=List.of(level,mode,section); return List.of();
    }
    @Override public List<CetItem> findAllItems() { return List.of(); }
    @Override public List<SectionResource> findSections() { return List.of(); }
  }
}
