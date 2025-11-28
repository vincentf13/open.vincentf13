package test.open.vincentf13.sdk.core.test.Sample;

/**
 測試用 MyBatis 實體，對應 mybatis_users 資料表。
 */
public class MybatisUser {
    
    private Long id;
    private String name;
    
    public MybatisUser() {
    }
    
    public MybatisUser(Long id,
                       String name) {
        this.id = id;
        this.name = name;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
