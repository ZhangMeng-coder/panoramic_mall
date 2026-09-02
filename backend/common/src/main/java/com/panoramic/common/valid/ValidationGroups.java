package com.panoramic.common.valid;

/**
 * 验证组标识接口
 * 用于区分不同的验证场景
 */
public class ValidationGroups {
    
    /**
     * 创建时的验证组
     */
    public interface Create {
    }
    
    /**
     * 更新时的验证组
     */
    public interface Update {
    }
    
    /**
     * 删除时的验证组
     */
    public interface Delete {
    }
}
