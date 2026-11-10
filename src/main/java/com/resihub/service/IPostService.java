package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.Post;

public interface IPostService extends IService<Post> {

    Result create(Post post);

    Result queryList(String category, Integer page);

    Result detail(Long id);

    /** 点赞/取消点赞 */
    Result toggleLike(Long id);

    Result comment(Long postId, String content);

    Result comments(Long postId);

    Result myPosts();

    Result hot();
}
