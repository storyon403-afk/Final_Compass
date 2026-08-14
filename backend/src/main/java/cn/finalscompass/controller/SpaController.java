package cn.finalscompass.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 将前端客户端路由转发到 SPA 入口，由浏览器继续完成页面路由。 */
@Controller
public class SpaController {
  @GetMapping({"/courses/{courseId}", "/courses/{courseId}/teachers/{teacherId}"})
  public String spa() {
    return "forward:/index.html";
  }
}
