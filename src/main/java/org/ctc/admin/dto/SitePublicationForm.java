package org.ctc.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SitePublicationForm {
    private String snapshotId;
    private String manifestSha256;
    private boolean confirmed;
}
