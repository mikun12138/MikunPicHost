package me.mikun.storage

import com.qcloud.cos.COSClient
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicCOSCredentials
import com.qcloud.cos.auth.COSCredentials
import com.qcloud.cos.http.HttpProtocol
import com.qcloud.cos.model.*
import com.qcloud.cos.region.Region
import io.ktor.server.application.*
import java.io.InputStream


class PicStorageCos : PicStorage() {
    private lateinit var cosClient: COSClient
    private lateinit var bucket: Bucket

    override fun init(application: Application) {
        with(application) {
            with(environment) {
                fun initClient() {
                    val cred: COSCredentials = BasicCOSCredentials(
                        config.property("storage.cos.secretId").getString(),
                        config.property("storage.cos.secretKey").getString()
                    )
                    val region = Region(config.property("storage.cos.region").getString())
                    val clientConfig = ClientConfig(region)
                    clientConfig.httpProtocol = HttpProtocol.https

                    cosClient = COSClient(
                        cred,
                        clientConfig
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
