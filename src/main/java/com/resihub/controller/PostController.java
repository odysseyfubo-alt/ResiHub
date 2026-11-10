package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.entity.Post;
import com.resihub.service.IPostService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/post")
public class PostController {

    @Resource
    private IPostService postService;

    /** 发邻里动态 */
    @PostMapping
    public Result create(@RequestBody Post post) {
        return postService.create(post);
    }

    /** 邻里圈列表（按分类筛选） */
    @GetMapping("/list")
    public Result list(@RequestParam(value = "category", required = false) String category,
                       @RequestParam(value = "page", defaultValue = "1") Integer page) {
        return postService.queryList(category, page);
    }

    /** 热门 */
    @GetMapping("/hot")
    public Result hot() {
        return postService.hot();
    }

    /** 我发的 */
    @GetMapping("/my")
    public Result my() {
        return postService.myPosts();
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return postService.detail(id);
    }

    /** 点赞/取消点赞 */
    @PutMapping("/like/{id}")
    public Result like(@PathVariable("id") Long id) {
        return postService.toggleLike(id);
    }

    @PostMapping("/{id}/comment")
    public Result comment(@PathVariable("id") Long id, @RequestParam("content") String content) {
        return postService.comment(id, content);
    }

    @GetMapping("/{id}/comments")
    public Result comments(@PathVariable("id") Long id) {
        return postService.comments(id);
    }
}
