/*
 *  MaplySphericalQuadEarthWithTexGroup.h
 *  WhirlyGlobe-MaplyComponent
 *
 *  Created by Steve Gifford on 1/24/13.
 *  Copyright 2011-2012 mousebird consulting
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

#import "MaplyViewControllerLayer.h"
#import <WhirlyGlobe.h>

@interface MaplySphericalQuadEarthWithTexGroup : MaplyViewControllerLayer

/// Initialize with a texture group.  Ideally the quad tree kind
- (id)initWithWithLayerThread:(WhirlyKitLayerThread *)layerThread  scene:(WhirlyKit::Scene *)scene renderer:(WhirlyKitSceneRendererES *)renderer texGroup:(NSString *)texGroupName;

/// Clean up any and all resources
- (void)cleanupLayers:(WhirlyKitLayerThread *)layerThread scene:(WhirlyKit::Scene *)scene;

@end