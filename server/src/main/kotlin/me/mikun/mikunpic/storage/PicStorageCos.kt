package me.mikun.mikunpic.storage

import com.qcloud.cos.COSClient
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicCOSCredentials
import com.qcloud.cos.auth.COSCredentials
import com.qcloud.cos.demo.GetObjectMetadataDemo
import com.qcloud.cos.http.HttpProtocol
import com.qcloud.cos.model.Bucket
import com.qcloud.cos.model.CannedAccessControlList
import com.qcloud.cos.model.CreateBucketRequest
import com.qcloud.cos.model.GetObjectMetadataRequest
import com.qcloud.cos.model.GetObjectRequest
import com.qcloud.cos.model.ListObjectsRequest
import com.qcloud.cos.model.ObjectMetadata
import com.qcloud.cos.model.PutObjectRequest
import com.qcloud.cos.model.ciModel.workflow.InputObjectInfoObject
import com.qcloud.cos.region.Region
import io.ktor.server.application.Application
import io.ktor.util.Digest
import me.mikun.mikunpic.dto.data.MikunPicConfig
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import java.io.InputStream

class PicStorageCos(
    override val label: String,
) : PicStorage() {
    private lateinit var cosClient: COSClient
    private lateinit var bucket: Bucket

    override fun init(
        application: Application,
        storage: MikunPicConfig.Storage,
    ) {
        (storage as? MikunPicConfig.Storage.Cos)?.let { storage ->

            fun initClient() {
                val cred: COSCredentials = BasicCOSCredentials(
                    storage.secretId,
                    storage.secretKey,
                )

                val region = Region(
                    storage.region,
                )
                val clientConfig = ClientConfig(region)
                clientConfig.httpProtocol = HttpProtocol.https

                cosClient = COSClient(
                    cred,
                    clientConfig,
                )
            }
            initClient()

            fun initBucket() {
                val bucketName = storage.bucketName
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

    override suspend fun random(): InputStream? = GetObjectRequest(
        bucket.name,
        picKeys.random(),
    ).let { request ->
        cosClient.getObject(request)
    }.objectContent

    override suspend fun hash(
        key: String,
    ): String? {
        val eTag = cosClient.getObjectMetadata(
            bucket.name,
            key
        ).eTag

        println(eTag.length)

        if (eTag.length == 32) {
            return eTag
        } else {
            cosClient.getObject(
                bucket.name,
                key,
            ).objectContent.let { inputStream ->
                return Digest("md5").let {
                    it += inputStream.readBytes()
                    it.build()
                }.toHexString()
            }
        }
    }

    override suspend fun byKey(
        key: String,
        thumbnail: OhMyRouting.Pic.Thumbnail,
    ): InputStream? {
        val reqKey = "$key${thumbnail.asParam()}"
        return cosClient.getObject(
            GetObjectRequest(
                bucket.name,
                reqKey,
            ),
        ).objectContent
    }

    override suspend fun upload(
        byteArray: ByteArray,
        storeKey: String,
    ) {
        val metadata = ObjectMetadata().apply {
            contentLength = byteArray.size.toLong()
        }

        val request = PutObjectRequest(
            bucket.name,
            storeKey,
            byteArray.inputStream(),
            metadata,
        )

        cosClient.putObject(request)
    }

    private fun OhMyRouting.Pic.Thumbnail.asParam(): String = when (this) {
        OhMyRouting.Pic.Thumbnail.Thumb -> "/thumb"
        OhMyRouting.Pic.Thumbnail.Small -> "/small"
        OhMyRouting.Pic.Thumbnail.Medium -> "/medium"
        OhMyRouting.Pic.Thumbnail.Large -> "/large"
        OhMyRouting.Pic.Thumbnail.Orig -> ""
    }
}
