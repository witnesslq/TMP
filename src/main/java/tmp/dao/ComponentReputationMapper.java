package tmp.dao;

import org.springframework.stereotype.Repository;
import tmp.entity.ComponentReputation;

import java.util.List;

@Repository
public interface ComponentReputationMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(ComponentReputation record);

    int insertSelective(ComponentReputation record);

    ComponentReputation selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(ComponentReputation record);

    int updateByPrimaryKey(ComponentReputation record);

    //查询对应组件的最近20条声誉记录
    List<ComponentReputation> queryReputationListByComponentUid(String componentUid);
}