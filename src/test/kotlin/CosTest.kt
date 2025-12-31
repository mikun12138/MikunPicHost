package me.mikun

import com.qcloud.cos.COSClient
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicSessionCredentials
import com.qcloud.cos.model.CannedAccessControlList
import com.qcloud.cos.model.CreateBucketRequest
import com.qcloud.cos.model.GeneratePresignedUrlRequest
import com.qcloud.cos.model.GetObjectRequest
import com.qcloud.cos.model.ListObjectsRequest
import com.qcloud.cos.model.ObjectMetadata
import com.qcloud.cos.model.PutObjectRequest
import com.qcloud.cos.region.Region
import com.qcloud.cos.utils.Jackson
import com.tencent.cloud.CosStsClient
import com.tencent.cloud.Policy
import com.tencent.cloud.Statement
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import java.io.File
import java.util.TreeMap
import kotlin.test.BeforeTest
import kotlin.test.Test

class CosTest {
    companion object {
        lateinit var cosClient: COSClient
        val testBucketName = "test-1324551995"
    }

    @BeforeTest
    fun preConfigTest() =
        testApplication {
            environment {
                config = ApplicationConfig("application.yaml")
                val statement =
                    Statement().apply {
                        setEffect("allow")
                        addActions(
                            arrayOf(
                                "cos:*",
                            ),
                        )
                        addResources(
                            arrayOf(
                                "*",
                            ),
                        )
                    }

                val policy =
                    Policy().apply {
                        addStatement(statement)
                    }

                val configMap =
                    TreeMap<String, Any>().apply {
                        putAll(
                            mapOf(
                                "secretId" to config.property("storage.cos.secretId").getString(),
                                "secretKey" to config.property("storage.cos.secretKey").getString(),
                                "durationSeconds" to 1800,
                                // 似乎不是必要
//                        "bucket" to "test-1324551995",
//                        "region" to "ap-shanghai",
                                "policy" to Jackson.toJsonPrettyString(policy),
                            ),
                        )
                    }

                val response =
                    CosStsClient.getCredential(configMap)

                val tmpSecretId =
                    response.credentials.tmpSecretId
                val tmpSecretKey =
                    response.credentials.tmpSecretKey
                val sessionToken =
                    response.credentials.sessionToken

                val cred =
                    BasicSessionCredentials(
                        tmpSecretId,
                        tmpSecretKey,
                        sessionToken,
                    )

                cosClient =
                    COSClient(
                        cred,
                        ClientConfig(
                            Region(
                                config.property("storage.cos.region").getString(),
                            ),
                        ),
                    )
            }
        }

    @Test
    fun listBucketsTest() {
        cosClient.listBuckets().forEach {
            println(it)
        }
    }

    @Test
    fun createBucketTest() {
        cosClient.listBuckets().find { it.name == testBucketName }
            ?: run {
                CreateBucketRequest(testBucketName)
                    .apply {
                        cannedAcl = CannedAccessControlList.PublicRead
                    }.let { request ->
                        cosClient.createBucket(request)
                    }
            }
    }

    @Test
    fun uploadFileTest() {
        val bucketName = testBucketName
        val toUpload = Thread.currentThread().contextClassLoader.getResource("rua.jpg")!!
        val key = "rua.jpg"
        PutObjectRequest(
            bucketName,
            key,
            toUpload.openStream(),
            ObjectMetadata(),
        ).let { request ->
            cosClient.putObject(request)
        }
    }

    @Test
    fun listObjectsTest() {
        ListObjectsRequest()
            .apply {
                bucketName = testBucketName
                prefix = ""
                maxKeys = 1000 // 最高一次1000
            }.let { request ->
                cosClient.listObjects(request)
            }.let { objectListing ->
                objectListing.objectSummaries.forEach {
                    println(it)
                }
                objectListing.marker = objectListing.nextMarker
                // ...设置下一个marker后进行下一轮查询
            }
    }

    @Test
    fun downloadObjectTest() {
        val key = "rua.jpg"
        val localFile = File("sandbox/rua_download.jpg")
        GetObjectRequest(
            testBucketName,
            key,
        ).apply {
            trafficLimit = 8 * 1024 * 1024 // 限速
        }.let { request ->
            cosClient.getObject(
                request,
                localFile,
            )
        }
    }

    @Test
    fun getUrlTest() {
        val key = "rua.jpg"
        cosClient
            .getObjectUrl(
                testBucketName,
                key,
            ).let {
                println(it)
            }
    }

    @Test
    fun getPresignedUrlTest() {
        val key = "rua.jpg"
        GeneratePresignedUrlRequest(
            testBucketName,
            key,
        ).let { request ->
            cosClient.generatePresignedUrl(request)
        }.let {
            println(it)
        }
    }

//    @Test
//    fun deleteObject() {
//        val key = "rua.jpg"
//        DeleteObjectRequest(
//            testBucketName,
//            key
//        ).let { request ->
//            cosClient.deleteObject(request)
//        }
//    }
//
//    @Test
//    fun deleteBucketTest() {
//        DeleteBucketRequest(testBucketName).let {
//            cosClient.deleteBucket(it)
//        }
//    }
}
