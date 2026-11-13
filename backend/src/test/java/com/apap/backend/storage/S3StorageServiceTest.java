package com.apap.backend.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class S3StorageServiceTest {

    private static final String KEY_PATTERN =
            "videos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-";

    @Test
    void S3업로드시_키형식은_videos_uuid_filename() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        S3StorageService storageService = new S3StorageService(s3Client, "project10-86-virg-apap-media");

        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.mp4", "video/mp4", "dummy".getBytes());

        String storedKey = storageService.store(file);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        assertThat(captor.getValue().bucket()).isEqualTo("project10-86-virg-apap-media");
        assertThat(captor.getValue().key()).matches(KEY_PATTERN + "sample\\.mp4");
        // sourceUrl에는 putObject에 사용한 키가 그대로 저장되어야 한다
        assertThat(storedKey).isEqualTo(captor.getValue().key());
    }

    @Test
    void 파일명에_경로나_위험문자가_있으면_정리된다() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        S3StorageService storageService = new S3StorageService(s3Client, "bucket");

        MockMultipartFile file = new MockMultipartFile(
                "file", "../etc/pass wd!.mp4", "video/mp4", "dummy".getBytes());

        String storedKey = storageService.store(file);

        // 경로 구분자 이전은 제거되고 공백/특수문자는 _로 치환된다
        assertThat(storedKey).matches(KEY_PATTERN + "pass_wd_\\.mp4");
    }
}
