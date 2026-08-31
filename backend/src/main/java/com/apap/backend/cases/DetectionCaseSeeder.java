package com.apap.backend.cases;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 7월 회의에서 정한 서비스 시나리오 4종을 초기 케이스로 등록한다.
 * 이름 기준으로 중복 삽입을 방지하므로 재기동해도 안전하다.
 */
@Component
public class DetectionCaseSeeder implements CommandLineRunner {

    private final DetectionCaseRepository detectionCaseRepository;

    public DetectionCaseSeeder(DetectionCaseRepository detectionCaseRepository) {
        this.detectionCaseRepository = detectionCaseRepository;
    }

    @Override
    public void run(String... args) {
        seed("한강 교각 위험 행동",
                "한강 교각 주변에서 난간에 오르는 등 위험 전조 행동 감지",
                "한강 교각 주변에서 위험 행동이 감지되었습니다. 즉시 확인이 필요합니다.");
        seed("식당 식권 미제출",
                "식당에서 식권을 제출하지 않고 줄을 서거나 입장하는 행동 감지",
                "식권 미제출 입장이 감지되었습니다. 확인해 주세요.");
        seed("영화관 무단입장",
                "영화관에서 표 확인 없이 입장하는 행동 감지",
                "표 확인 없이 입장한 관객이 감지되었습니다. 확인해 주세요.");
        seed("마트 주머니 은닉",
                "마트에서 상품을 주머니·가방에 넣는 행동 감지",
                "상품 은닉 의심 행동이 감지되었습니다. 확인이 필요합니다.");
    }

    private void seed(String name, String description, String outMsg) {
        if (!detectionCaseRepository.existsByName(name)) {
            detectionCaseRepository.save(new DetectionCase(name, description, outMsg));
        }
    }
}
