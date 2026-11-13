package com.apap.backend.cases;

import com.apap.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 감지 케이스(서비스 시나리오). 7월 회의 예시: 한강 교각 위험 행동, 식당 식권 미제출,
 * 영화관 무단입장, 마트 주머니 은닉.
 * out_msg는 비정상 판정 시 사용자에게 전달할 메시지다.
 */
@Entity
@Table(name = "detection_cases")
public class DetectionCase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false, length = 300)
    private String outMsg;

    protected DetectionCase() {
    }

    public DetectionCase(String name, String description, String outMsg) {
        this.name = name;
        this.description = description;
        this.outMsg = outMsg;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOutMsg() {
        return outMsg;
    }
}
