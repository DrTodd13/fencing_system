import numpy as np
import tensorflow as tf
from tensorflow.keras.models import Sequential, Model
from tensorflow.keras.layers import Dense, LSTM, Dropout, Masking, Input, Layer, TimeDistributed
from sklearn.model_selection import train_test_split, KFold
from tensorflow.keras.callbacks import EarlyStopping
from tensorflow.keras.regularizers import l2
import os

prefixes = ["C:\\Users\\drtod\\Documents\\fencing\\Shanghai_Red", "C:\\Users\\drtod\\Documents\\fencing\\Shanghai_Yellow"]

def add_noise(x, y, invariant, noise_level=0.05):
    """
    Adds a bit of noise to all the x,y points that are present.
    Data points that are 0 should stay zero.
    """
    data = np.copy(x)
    xdata_min = np.min(data[data != invariant])    
    xdata_max = np.max(data[data != invariant])    
    xrange = noise_level * (xdata_max - xdata_min)
    nonzero_data_mask = data != invariant
    nonzero_data = data[nonzero_data_mask]
    nonzero_data += (noise_level * xrange) * np.random.randn(*nonzero_data.shape)
    return data, np.copy(y)

def add_shift(x, y, invariant):
    data = np.copy(x)
    xdata = data[:,:,::2]  # get x-coords from the data
    ydata = data[:,:,1::2] # get y-coords from the data
    # Get minimum and maximum values for x and y coordinates.
    xdata_min = np.min(xdata[xdata != invariant])    
    xdata_max = np.max(xdata[xdata != invariant])    
    ydata_min = np.min(ydata[ydata != invariant])    
    ydata_max = np.max(ydata[ydata != invariant])
    # Scale changes to about 3%.
    xrange = 0.03 * (xdata_max - xdata_min)
    yrange = 0.03 * (ydata_max - ydata_min)
    
    # Randomly generate a x-coordinate shift that is +/- up to 3%.
    xshift = (2 * xrange) * np.random.randn() - xrange
    yshift = (2 * yrange) * np.random.randn() - yrange
    
    xdata[xdata != invariant] += xshift
    ydata[ydata != invariant] += yshift

    return data, np.copy(y)

def add_scale(x, y, invariant):
    data = np.copy(x)
    scale_factor = np.random.rand() * 0.2
    nonzero_data_mask = data != invariant
    nonzero_data = data[nonzero_data_mask]
    nonzero_data *= scale_factor
    return data, np.copy(y)
    
def load_from_prefix(prefix):
    fdir = prefix
    prefix = os.path.basename(fdir)

    # Load the data
    joints = np.load(fdir + '/' + prefix + '_training.npy')
    right_of_way = np.load(fdir + '/' + prefix + '_ground_truth.npy')
    # The right-of-way data file has -1 for left priority and 1 for
    # right priority.  Change all the -1 to 0 for more normal training.
    right_of_way[right_of_way == -1] = 0

    video_size = np.load(fdir + '/' + prefix + '_video_size.npy')
    # Get the width and height of the video
    width = video_size[0]
    #height = video_size[1]
    
    """
    new_data = []
    # Add 4 slight modifications to the input data but mapping to the same
    # results to extend the data set and try to prevent overfitting.
    for i in range(0):
        #foo_augmented = add_noise(joints)
        foo_augmented = add_shift(joints)
        new_data.append((foo_augmented, np.copy(right_of_way)))
        
    # Concatenate the new data with the original data.
    for augmented in new_data:
        joints = np.concatenate((joints, augmented[0]), axis=0)
        right_of_way = np.concatenate((right_of_way, augmented[1]), axis=0)
    """
    
    # Make a copy of the augmented data.
    # Generate a left-right flipped version of the x coordinates.
    # Add that to the data to train on, again so that we have more data points
    # and to generalize better.
    reversed_joints = np.copy(joints)
    # Just get x coords by skip 2.
    rj_temp = reversed_joints[:,:,::2]
    # Flip x coords by subtracting from the frame width, making sure
    # not to modify 0 values.
    rj_temp[rj_temp != 0] = width - rj_temp[rj_temp != 0]
    # Concatenate the new data with the flipped data.
    joints = np.concatenate((joints, reversed_joints), axis=0)
    # Add to the ground truth but since left-right is flipped, the results
    # need to be flipped as well.
    right_of_way = np.concatenate((right_of_way, 1 - right_of_way), axis=0)
    
    # Custom normalization function
    def custom_normalize(data):
        """
        Normalization is subtracting the mean and dividing by the
        standard deviation.  Right now, we are normalizing x and y coords
        the same but we could do those separately.  Don't know what effect
        that would have.  Again, be sure to only normalize the non-zero values.
        Some of those previously non-zero values might become 0 through
        normalization so rememeber the locations of the original 0's and
        change them to -9999.0.
        """
        nonzero_data_mask = data != 0
        zero_data_mask = data == 0
        nonzero_data = data[nonzero_data_mask]
        data_mean = np.mean(nonzero_data)
        data_std = np.std(nonzero_data)
        data[nonzero_data_mask] = (data[nonzero_data_mask] - data_mean) / data_std
        data[zero_data_mask] = -9999.0
        return data
    
    # Normalize the data.
    joints_normalized = custom_normalize(joints)
    return (joints_normalized, right_of_way)

def augment(x, y, invariant):
    new_data = []
    # Add 4 slight modifications to the input data but mapping to the same
    # results to extend the data set and try to prevent overfitting.
    for i in range(5):
        if i < 3:
            aug_type = i
        else:
            aug_type = np.random.randint(0, 3)
        if aug_type == 0:
            print("Augmenting with noise")
            x_augmented, y_augmented = add_noise(x, y, invariant)
        elif aug_type == 1:
            print("Augmenting with shift")
            x_augmented, y_augmented = add_shift(x, y, invariant)
        elif aug_type == 2:
            print("Augmenting with scale")
            x_augmented, y_augmented = add_scale(x, y, invariant)
        else:
            assert False
        new_data.append((x_augmented, y_augmented))
        
    # Concatenate the new data with the original data.
    for augmented in new_data:
        x = np.concatenate((x, augmented[0]), axis=0)
        y = np.concatenate((y, augmented[1]), axis=0)
        
    return x, y
    
loaded_data = [load_from_prefix(x) for x in prefixes]
for i in range(1, len(loaded_data)):
    np.concatenate((loaded_data[0][0], loaded_data[i][0]), axis=0)
    np.concatenate((loaded_data[0][1], loaded_data[i][1]), axis=0)

joints_normalized = loaded_data[0][0]
right_of_way = loaded_data[0][1]

pre_split_augment = True
post_split_augment = False

if pre_split_augment:
    joints_normalized, right_of_way = augment(joints_normalized, right_of_way, -9999.0)
    
# Split the data into training and testing sets
X_train, X_test, y_train, y_test = train_test_split(joints_normalized, right_of_way, test_size=0.2, random_state=42)

if post_split_augment:
    X_train, y_train = augment(X_train, y_train, -9999.0)

class Attention(Layer):
    def __init__(self, **kwargs):
        super(Attention, self).__init__(**kwargs)

    def build(self, input_shape):
        self.W = self.add_weight(name='attention_weight', shape=(input_shape[-1], input_shape[-1]), initializer='random_normal', trainable=True)
        self.b = self.add_weight(name='attention_bias', shape=(input_shape[-1],), initializer='zeros', trainable=True)
        super(Attention, self).build(input_shape)

    def call(self, inputs):
        e = tf.nn.tanh(tf.tensordot(inputs, self.W, axes=1) + self.b)
        a = tf.nn.softmax(e, axis=1)
        output = inputs * a
        return tf.reduce_sum(output, axis=1)

    def compute_output_shape(self, input_shape):
        return input_shape[0], input_shape[-1]
    
# Build the model with L2 regularization
def create_model():
    # Define the input
    input_layer = Input(shape=(125, 48)) 
    # LSTM layers 
    lstm_out = LSTM(64, return_sequences=True, kernel_regularizer=l2(0.01))(input_layer)
    lstm_out = Dropout(0.3)(lstm_out) 
    lstm_out = LSTM(64, return_sequences=True, kernel_regularizer=l2(0.01))(lstm_out) 
    lstm_out = Dropout(0.3)(lstm_out) 
    lstm_out = LSTM(64, return_sequences=True, kernel_regularizer=l2(0.01))(lstm_out) 
    lstm_out = Dropout(0.3)(lstm_out) 
    # Attention layer 
    lstm_out = Attention()(lstm_out) 
    # Output layer 
    output_layer = Dense(1, activation='sigmoid', kernel_regularizer=l2(0.01))(lstm_out) 
    # Build the model 
    model = Model(inputs=input_layer, outputs=output_layer)

    """    
    model = Sequential([
        Masking(mask_value=-9999.0, input_shape=(125, 48)),
        LSTM(64, return_sequences=True, kernel_regularizer=l2(0.01)),
        Dropout(0.3),
        LSTM(64, return_sequences=True, kernel_regularizer=l2(0.01)),
        Dropout(0.3),
        LSTM(64, kernel_regularizer=l2(0.01)),
        Dropout(0.3),
        Dense(1, activation='sigmoid', kernel_regularizer=l2(0.01))
    ])
    """    
    
    model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])
    return model

do_kfold = False

if do_kfold:
    # Early stopping callback
    early_stopping = EarlyStopping(monitor='val_loss', patience=10, restore_best_weights=True)

    # Cross-validation
    kf = KFold(n_splits=1, shuffle=True, random_state=42)
    
    best_model = None
    best_val_loss = float('inf')
    for train_index, val_index in kf.split(X_train):
        X_train_cv, X_val_cv = X_train[train_index], X_train[val_index]
        y_train_cv, y_val_cv = y_train[train_index], y_train[val_index]
        model = create_model()
        model.fit(X_train_cv, y_train_cv, epochs=200, batch_size=16, validation_data=(X_val_cv, y_val_cv), callbacks=[early_stopping])
        val_loss = model.evaluate(X_val_cv, y_val_cv, verbose=0)[0]
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_model = model
else:
    # Early stopping callback
    early_stopping = EarlyStopping(monitor='loss', patience=10, restore_best_weights=True)
    model = create_model()
    model.fit(X_train, y_train, epochs=200, batch_size=16, callbacks=[early_stopping])
    best_model = model
    
# To load, do:
# from keras.models import load_model
# new_model = load_model(filepath)
best_model.save("ttfencing_model")

# Evaluate the best model on the test set
loss, accuracy = best_model.evaluate(X_test, y_test)
print(f'Test Loss: {loss}')
print(f'Test Accuracy: {accuracy}')