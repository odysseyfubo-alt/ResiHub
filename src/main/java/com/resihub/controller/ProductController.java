package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.service.IProductService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/product")
public class ProductController {

    @Resource
    private IProductService productService;

    /** 本小区团购商品 */
    @GetMapping("/list")
    public Result queryList() {
        return productService.queryList();
    }

    /** 商品详情（逻辑过期缓存） */
    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return productService.detail(id);
    }
}
