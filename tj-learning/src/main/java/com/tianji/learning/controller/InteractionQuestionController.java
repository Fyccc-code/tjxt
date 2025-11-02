package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author Fyc
 * @since 2025-10-26
 */

@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
@Api(tags = "互动问答相关接口")
public class InteractionQuestionController {


    private final IInteractionQuestionService questionService;

    @ApiOperation("新增互动问题")
    @PostMapping
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionDTO) {
        questionService.saveQuestion(questionDTO);
    }

    @ApiOperation("分页查询互动问题")
    @GetMapping("page")
    public PageDTO<QuestionVO> queryQuestionPageAdmin(QuestionPageQuery query) {
        return questionService.queryQuestionPage(query);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询问题详情")
    public QuestionVO queryQuestionById(@PathVariable("id") Long id) {
        return questionService.queryQuestionById(id);
    }


}
