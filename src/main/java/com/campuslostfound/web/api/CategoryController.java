package com.campuslostfound.web.api;

import com.campuslostfound.domain.Category;
import com.campuslostfound.web.dto.ListingDtos.CategoryResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The fixed item taxonomy, as {value, label} pairs for a client to render. Public. */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    @GetMapping
    public List<CategoryResponse> list() {
        return Arrays.stream(Category.values())
                .map(c -> new CategoryResponse(c.name(), c.label()))
                .toList();
    }
}
