package com.david.agent.document.schema.parse;

import com.david.agent.document.schema.model.SchemaDocument;

public interface SchemaDocumentParser {

    boolean supports(String content, String fileName);

    SchemaDocument parse(String content, String fileName);
}
