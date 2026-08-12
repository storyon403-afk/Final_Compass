package cn.finalscompass.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
  @GetMapping({"/courses/{courseId}", "/courses/{courseId}/teachers/{teacherId}"})
  public String spa() {
    return "forward:/index.html";
  }
}
