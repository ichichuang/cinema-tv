package com.ichichuang.cinema.data.local.entity

import androidx.room.*

/** Durable user intent; generations are floors, never wall-clock timestamps. */
@Entity(tableName="personal_library_state", foreignKeys=[ForeignKey(entity=WorkReferenceEntity::class,
    parentColumns=["workId"],childColumns=["workId"],onDelete=ForeignKey.RESTRICT)])
internal data class PersonalLibraryStateEntity(
    @PrimaryKey @ColumnInfo(typeAffinity=ColumnInfo.BLOB) val workId:String,
    val hiddenThroughGeneration:Long=-1,
    val deletedThroughGeneration:Long=-1,
    val favoriteOrder:Long=0,
    val favoriteRevision:Long=0,
    val undoneRevision:Long=0,
)

@Entity(tableName="confirmed_plays", indices=[Index(value=["sessionGeneration"],unique=true),Index(value=["workId"])],
    foreignKeys=[ForeignKey(entity=WorkReferenceEntity::class,parentColumns=["workId"],childColumns=["workId"],onDelete=ForeignKey.RESTRICT),
        ForeignKey(entity=PlaybackSessionEntity::class,parentColumns=["generation"],childColumns=["sessionGeneration"],onDelete=ForeignKey.RESTRICT)])
internal data class ConfirmedPlayEntity(@PrimaryKey(autoGenerate=true) val eventOrder:Long=0,
    val sessionGeneration:Long,@ColumnInfo(typeAffinity=ColumnInfo.BLOB) val workId:String)

/** Kept independently of evictable metadata/source availability. Identity is never inferred from labels. */
@Entity(tableName="personal_library_cards",foreignKeys=[ForeignKey(entity=WorkReferenceEntity::class,
    parentColumns=["workId"],childColumns=["workId"],onDelete=ForeignKey.RESTRICT)])
internal data class PersonalLibraryCardEntity(@PrimaryKey @ColumnInfo(typeAffinity=ColumnInfo.BLOB) val workId:String,
    val title:String,val sourceLabel:String?)
