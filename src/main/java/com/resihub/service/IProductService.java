package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.Product;

public interface IProductService extends IService<Product> {

    Result queryList();

    Result detail(Long id);
}
