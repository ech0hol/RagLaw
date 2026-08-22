package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.dto.CategoryDto;
import com.raglaw.rag.dto.CategoryTreeNode;
import com.raglaw.rag.dto.CreateCategoryRequest;
import com.raglaw.rag.dto.UpdateCategoryRequest;
import com.raglaw.rag.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories/tree")
    public ApiResponse<List<CategoryTreeNode>> tree() {
        return ApiResponse.ok(categoryService.getTree(true));
    }

    @GetMapping("/admin/categories")
    public ApiResponse<List<CategoryTreeNode>> adminTree() {
        return ApiResponse.ok(categoryService.getTree(false));
    }

    @GetMapping("/admin/categories/{id}")
    public ApiResponse<CategoryDto> get(@PathVariable String id) {
        return ApiResponse.ok(categoryService.getById(id));
    }

    @PostMapping("/admin/categories")
    public ApiResponse<CategoryDto> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.ok(categoryService.create(request));
    }

    @PutMapping("/admin/categories/{id}")
    public ApiResponse<CategoryDto> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ApiResponse.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/admin/categories/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ApiResponse.ok(null);
    }
}
