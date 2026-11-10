package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.entity.VisitPass;
import com.resihub.service.IVisitPassService;
import com.resihub.utils.RequireRole;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/visit-pass")
public class VisitPassController {

    @Resource
    private IVisitPassService visitPassService;

    /** 业主生成访客通行码 */
    @PostMapping
    public Result create(@RequestBody VisitPass form,
                         @RequestParam(value = "hours", required = false) Integer hours) {
        return visitPassService.create(form, hours);
    }

    /** 门禁核销（门岗人员） */
    @PostMapping("/verify")
    @RequireRole({1, 2, 3})
    public Result verify(@RequestParam("code") String code) {
        return visitPassService.verify(code);
    }

    /** 我的访客记录 */
    @GetMapping("/my")
    public Result my() {
        return visitPassService.myPasses();
    }
}
