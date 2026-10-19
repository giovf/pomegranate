package com.gankdat.pomegranate.data.db

import androidx.room.TypeConverter
import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MediaGroupKind
import com.gankdat.pomegranate.data.db.entity.OriginSource
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import com.gankdat.pomegranate.data.db.entity.TransferState

/**
 * Stores every enum by its constant name as TEXT. Explicit rather than relying
 * on Room's implicit enum handling so the on-disk format is pinned by this
 * file and the exported schema, independent of Room's defaults.
 *
 * Match evidence is kept as a JSON string column and needs no converter.
 */
internal class Converters {
    @TypeConverter fun fromMediaGroupKind(value: MediaGroupKind): String = value.name

    @TypeConverter fun toMediaGroupKind(value: String): MediaGroupKind = MediaGroupKind.valueOf(value)

    @TypeConverter fun fromMatchConfidence(value: MatchConfidence): String = value.name

    @TypeConverter fun toMatchConfidence(value: String): MatchConfidence = MatchConfidence.valueOf(value)

    @TypeConverter fun fromTransferState(value: TransferState): String = value.name

    @TypeConverter fun toTransferState(value: String): TransferState = TransferState.valueOf(value)

    @TypeConverter fun fromFidelity(value: Fidelity): String = value.name

    @TypeConverter fun toFidelity(value: String): Fidelity = Fidelity.valueOf(value)

    @TypeConverter fun fromOriginSource(value: OriginSource): String = value.name

    @TypeConverter fun toOriginSource(value: String): OriginSource = OriginSource.valueOf(value)

    @TypeConverter fun fromPickerRoundState(value: PickerRoundState): String = value.name

    @TypeConverter fun toPickerRoundState(value: String): PickerRoundState = PickerRoundState.valueOf(value)
}
