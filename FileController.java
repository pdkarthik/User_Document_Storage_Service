import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private AmazonS3 s3Client;

    @Value("${s3.bucket.name}")
    private String bucketName;

    @GetMapping("/{userName}")
    public ResponseEntity<List<String>> searchFiles(
            @PathVariable String userName,
            @RequestParam(required = false) String searchTerm
    ) {
        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucketName)
                .withPrefix(userName + "/");
        ListObjectsV2Result result = s3Client.listObjectsV2(request);
        List<String> fileNames = result.getObjectSummaries().stream()
                .filter(summary -> searchTerm == null || summary.getKey().contains(searchTerm))
                .map(S3ObjectSummary::getKey)
                .collect(Collectors.toList());
        return ResponseEntity.ok(fileNames);
    }

    @GetMapping("/{userName}/{fileName}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String userName,
            @PathVariable String fileName
    ) throws IOException {
        GetObjectRequest request = new GetObjectRequest(bucketName, userName + "/" + fileName);
        S3Object object = s3Client.getObject(request);
        S3ObjectInputStream inputStream = object.getObjectContent();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        byte[] data = outputStream.toByteArray();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(data.length);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.builder("attachment").filename(fileName).build());
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @PostMapping("/{userName}")
    public ResponseEntity<String> uploadFile(
            @PathVariable String userName,
            @RequestParam("file") MultipartFile multipartFile
    ) throws IOException {
        String fileName = userName + "/" + multipartFile.getOriginalFilename();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(multipartFile.getSize());
        s3Client.putObject(bucketName, fileName, multipartFile.getInputStream(), metadata);
        return ResponseEntity.ok("File uploaded successfully");
    }

}
