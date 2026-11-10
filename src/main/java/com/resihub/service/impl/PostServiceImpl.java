package com.resihub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.entity.Post;
import com.resihub.entity.PostComment;
import com.resihub.entity.User;
import com.resihub.mapper.PostCommentMapper;
import com.resihub.mapper.PostMapper;
import com.resihub.mapper.UserMapper;
import com.resihub.service.IPointsService;
import com.resihub.service.IPostService;
import com.resihub.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {

    public static final String POST_LIKED_KEY = "post:liked:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserMapper userMapper;
    @Resource
    private PostCommentMapper commentMapper;
    @Resource
    private IPointsService pointsService;

    @Override
    public Result create(Post post) {
        Long userId = UserHolder.getUserId();
        Long communityId = UserHolder.getCommunityId();
        if (communityId == null) {
            return Result.fail("请先选择小区");
        }
        if (post.getTitle() == null || post.getTitle().trim().isEmpty()) {
            return Result.fail("请填写标题");
        }
        Post p = new Post()
                .setCommunityId(communityId)
                .setUserId(userId)
                .setCategory(post.getCategory() == null || post.getCategory().trim().isEmpty() ? "分享" : post.getCategory().trim())
                .setTitle(post.getTitle().trim())
                .setContent(post.getContent() == null ? "" : post.getContent())
                .setImages(post.getImages() == null ? "" : post.getImages())
                .setLiked(0)
                .setComments(0)
                .setStatus(1);
        save(p);
        pointsService.addPoints(userId, 2, "post", "发布邻里动态");
        return Result.ok(p.getId());
    }

    @Override
    public Result queryList(String category, Integer page) {
        Long communityId = UserHolder.getCommunityId();
        if (communityId == null) {
            return Result.fail("请先选择小区");
        }
        Page<Post> p = query()
                .eq("community_id", communityId)
                .eq("status", 1)
                .eq(category != null && !category.trim().isEmpty(), "category", category)
                .orderByDesc("create_time")
                .page(new Page<>(page == null ? 1 : page, 20));
        fill(p.getRecords());
        return Result.ok(p.getRecords(), p.getTotal());
    }

    @Override
    public Result detail(Long id) {
        Post post = getById(id);
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            return Result.fail("帖子不存在");
        }
        fill(java.util.Collections.singletonList(post));
        return Result.ok(post);
    }

    @Override
    public Result toggleLike(Long id) {
        Post post = getById(id);
        if (post == null) {
            return Result.fail("帖子不存在");
        }
        Long userId = UserHolder.getUserId();
        String key = POST_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            boolean ok = update().setSql("liked = liked + 1").eq("id", id).update();
            if (ok) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
            return Result.ok(true);
        }
        boolean ok = update().setSql("liked = liked - 1").eq("id", id).gt("liked", 0).update();
        if (ok) {
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }
        return Result.ok(false);
    }

    @Override
    public Result comment(Long postId, String content) {
        if (content == null || content.trim().isEmpty()) {
            return Result.fail("评论内容不能为空");
        }
        Post post = getById(postId);
        if (post == null) {
            return Result.fail("帖子不存在");
        }
        Long userId = UserHolder.getUserId();
        PostComment c = new PostComment()
                .setPostId(postId)
                .setUserId(userId)
                .setContent(content.trim());
        commentMapper.insert(c);
        update().setSql("comments = comments + 1").eq("id", postId).update();
        return Result.ok(c.getId());
    }

    @Override
    public Result comments(Long postId) {
        List<PostComment> list = commentMapper.selectList(new QueryWrapper<PostComment>()
                .eq("post_id", postId)
                .orderByAsc("create_time"));
        for (PostComment c : list) {
            User u = userMapper.selectById(c.getUserId());
            c.setUserName(u == null ? "邻居" : u.getNickName());
        }
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result myPosts() {
        List<Post> list = query()
                .eq("user_id", UserHolder.getUserId())
                .orderByDesc("create_time")
                .list();
        fill(list);
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result hot() {
        Long communityId = UserHolder.getCommunityId();
        List<Post> list = query()
                .eq(communityId != null, "community_id", communityId)
                .eq("status", 1)
                .orderByDesc("liked")
                .last("limit 20")
                .list();
        fill(list);
        return Result.ok(list, (long) list.size());
    }

    private void fill(List<Post> list) {
        Long userId = UserHolder.getUserId();
        for (Post p : list) {
            User u = userMapper.selectById(p.getUserId());
            if (u != null) {
                p.setUserName(u.getNickName());
                p.setUserIcon(u.getIcon());
            }
            if (userId != null) {
                Double score = stringRedisTemplate.opsForZSet().score(POST_LIKED_KEY + p.getId(), userId.toString());
                p.setIsLike(score != null);
            }
        }
    }

    /** 供其他模块判断归属 */
    public boolean belongsToCommunity(Post post, Long communityId) {
        return Objects.equals(post.getCommunityId(), communityId);
    }
}
