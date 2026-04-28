package me.mikun.storage

import com.qcloud.cos.COSClient
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicSessionCredentials
import com.qcloud.cos.model.Bucket
import com.qcloud.cos.model.CannedAccessControlList
import com.qcloud.cos.model.CreateBucketRequest
import com.qcloud.cos.model.GetObjectRequest
import com.qcloud.cos.model.ListObjectsRequest
import com.qcloud.cos.region.Region
import com.qcloud.cos.utils.Jackson
import com.tencent.cloud.CosStsClient
import com.tencent.cloud.Policy
import com.tencent.cloud.Statement
import io.ktor.server.application.Application
import java.io.InputStream
import java.util.TreeMap

class PicStorageCos : PicStorage() {
    private lateinit var cosClient: COSClient
    private lateinit var bucket: Bucket

    override fun init(application: Application) {
        with(application) {
            with(environment) {
                fun initClient() {
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
                initClient()

                fun initBucket() {
                    val bucketName = config.property("storage.cos.bucket_name").getString()
                    bucket =
                        if (cosClient.doesBucketExist(bucketName)) {
                            cosClient.listBuckets().first { it.name == bucketName }
                        } else {
                            CreateBucketRequest(bucketName)
                                .apply {
                                    cannedAcl = CannedAccessControlList.PublicRead
                                }.let { request ->
                                    cosClient.createBucket(request)
                                }
                        }

                    var lastMarker = ""
                    while (true) {
                        ListObjectsRequest()
                            .apply {
                                this.bucketName = bucket.name
                                prefix = ""
                                maxKeys = 1000
                                marker = lastMarker
                            }.let { request ->
                                cosClient.listObjects(request)
                            }.let { objectListing ->
                                picKeys.addAll(
                                    objectListing.objectSummaries.map { it.key },
                                )
                                if (objectListing.nextMarker == null) break
                                lastMarker = objectListing.nextMarker
                            }
                    }
                }
                initBucket()
            }
        }
    }

    override suspend fun random(): InputStream? =
        GetObjectRequest(
            bucket.name,
            picKeys.random(),
        ).let { request ->
            cosClient.getObject(request)
        }.objectContent
}
