
/********************************************************************************
 * Copyright (c) 2014-2018 WANdisco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Apache License, Version 2.0
 *
 ********************************************************************************/
 
// Copyright (C) 2010 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.events;

import com.google.common.base.Supplier;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ApprovalAttribute;

public class CommentAddedEvent extends PatchSetEvent {
  static final String TYPE = "comment-added";
  public Supplier<AccountAttribute> author;
  public Supplier<ApprovalAttribute[]> approvals;
  public String comment;

  public CommentAddedEvent(Change change) {
    super(TYPE, change);
  }

  public CommentAddedEvent(CommentAddedEvent e, String type){
    this(e, type, false);
  }

  public CommentAddedEvent(CommentAddedEvent e, String type, boolean replicated){
    super(e, type, replicated);
    this.author = e.author;
    this.approvals = e.approvals;
    this.comment = e.comment;
  }
}
