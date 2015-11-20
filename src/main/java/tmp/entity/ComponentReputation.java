package tmp.entity;

import java.math.BigDecimal;
import java.util.Date;

public class ComponentReputation {
    private Integer id;

    private String uid;

    private String componentUid;

    private BigDecimal reputationValue;

    private Date createTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid == null ? null : uid.trim();
    }

    public String getComponentUid() {
        return componentUid;
    }

    public void setComponentUid(String componentUid) {
        this.componentUid = componentUid == null ? null : componentUid.trim();
    }

    public BigDecimal getReputationValue() {
        return reputationValue;
    }

    public void setReputationValue(BigDecimal reputationValue) {
        this.reputationValue = reputationValue;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "ComponentReputation{" + "id=" + id + ", uid='" + uid + '\'' + ", componentUid='" + componentUid + '\''
                + ", reputationValue=" + reputationValue + ", createTime=" + createTime + '}';
    }
}