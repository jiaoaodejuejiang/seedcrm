package com.seedcrm.crm.clue.service;

import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileResponse;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileUpsertRequest;
import java.util.Collection;
import java.util.List;

public interface ClueProfileService {

    ClueProfileResponse saveProfile(ClueProfileUpsertRequest request, Long updatedBy);

    List<ClueProfileResponse> listByClueIds(Collection<Long> clueIds);
}
