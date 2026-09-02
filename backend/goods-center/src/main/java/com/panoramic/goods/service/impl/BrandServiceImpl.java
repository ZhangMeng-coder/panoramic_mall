package com.panoramic.goods.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.goods.dto.BrandPageQueryDTO;
import com.panoramic.goods.dto.BrandSaveDTO;
import com.panoramic.goods.dto.BrandUpdateDTO;
import com.panoramic.goods.entity.GoodsBrand;
import com.panoramic.goods.entity.GoodsSpu;
import com.panoramic.goods.mapper.GoodsBrandMapper;
import com.panoramic.goods.mapper.GoodsSpuMapper;
import com.panoramic.goods.service.BrandService;
import com.panoramic.goods.vo.BrandVO;
import com.panoramic.goods.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品品牌服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final GoodsBrandMapper brandMapper;
    private final GoodsSpuMapper spuMapper;

    @Override
    public PageResult<BrandVO> page(BrandPageQueryDTO dto) {
        Page<GoodsBrand> brandPage = dto.toPage(GoodsBrand.class);
        IPage<GoodsBrand> result = brandMapper.selectPage(brandPage,
                Wrappers.<GoodsBrand>lambdaQuery()
                        .like(StringUtils.hasText(dto.getKeyword()), GoodsBrand::getName, dto.getKeyword())
                        .orderByAsc(GoodsBrand::getSort)
                        .orderByDesc(GoodsBrand::getId));

        List<BrandVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public List<BrandVO> listAll() {
        return brandMapper.selectList(
                        Wrappers.<GoodsBrand>lambdaQuery()
                                .orderByAsc(GoodsBrand::getSort)
                                .orderByAsc(GoodsBrand::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public BrandVO detail(Long id) {
        return toVO(getByIdOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveBrand(BrandSaveDTO dto) {
        checkNameDuplicate(dto.getName(), null);
        GoodsBrand brand = new GoodsBrand();
        BeanUtils.copyProperties(dto, brand);
        brand.setSort(dto.getSort() == null ? 0 : dto.getSort());
        brandMapper.insert(brand);
        return brand.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBrand(Long id, BrandUpdateDTO dto) {
        GoodsBrand brand = getByIdOrThrow(id);
        checkNameDuplicate(dto.getName(), id);
        BeanUtils.copyProperties(dto, brand);
        if (dto.getSort() == null) {
            brand.setSort(0);
        }
        brandMapper.updateById(brand);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBrand(Long id) {
        getByIdOrThrow(id);
        Long spuCount = spuMapper.selectCount(
                Wrappers.<GoodsSpu>lambdaQuery().eq(GoodsSpu::getBrandId, id));
        if (spuCount > 0) {
            throw new ServiceException("该品牌下存在商品，无法删除");
        }
        brandMapper.deleteById(id);
    }

    /**
     * 根据 ID 查询品牌（不存在抛出业务异常）
     *
     * @param id 品牌ID
     * @return 品牌实体
     */
    private GoodsBrand getByIdOrThrow(Long id) {
        GoodsBrand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new ServiceException("品牌不存在");
        }
        return brand;
    }

    /**
     * 校验品牌名称不重复
     *
     * @param name      品牌名称
     * @param excludeId 需要排除的品牌ID（更新时排除自身），可为 null
     */
    private void checkNameDuplicate(String name, Long excludeId) {
        Long count = brandMapper.selectCount(
                Wrappers.<GoodsBrand>lambdaQuery()
                        .eq(GoodsBrand::getName, name)
                        .ne(excludeId != null, GoodsBrand::getId, excludeId));
        if (count > 0) {
            throw new ServiceException("品牌名称已存在");
        }
    }

    /**
     * 实体转 VO
     *
     * @param brand 品牌实体
     * @return 品牌响应
     */
    private BrandVO toVO(GoodsBrand brand) {
        BrandVO vo = new BrandVO();
        BeanUtils.copyProperties(brand, vo);
        return vo;
    }
}
